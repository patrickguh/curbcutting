package ca.curbcutting.audit;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Optional;
import ca.curbcutting.audit.ScanStatus;

@Service
public class ScanService {

    private static final int MAX_ATTEMPTS = 3;

    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final SiteCrawler siteCrawler;

    public ScanService(ScanJobRepository scanJobRepository,
                       PageRepository pageRepository,
                       ViolationRepository violationRepository,
                       SiteCrawler siteCrawler) {
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
        this.siteCrawler = siteCrawler;
    }

    @Transactional
    public ScanJob enqueue(String url) {
        return scanJobRepository.save(new ScanJob(url));
    }

    @Transactional
    public Optional<ScanJob> claimNextJob() {
        return scanJobRepository.claimNextQueued().map(job -> {
            job.setStatus(ScanStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            return job;
        });
    }

    public List<String> discoverPages(String rootUrl) {
        return siteCrawler.discover(rootUrl);
    }

    public AxeResults runAxeOn(String url) {          // was private
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            com.microsoft.playwright.Page browserPage = browser.newPage();
            browserPage.navigate(url);
            AxeResults results = new AxeBuilder(browserPage).analyze();
            browser.close();
            return results;
        }
    }

    @Transactional
    public void storeResults(UUID jobId, String url, AxeResults results) {
        ScanJob job = scanJobRepository.findById(jobId).orElseThrow();
        Page page = pageRepository.save(new Page(job, url));

        List<Violation> toSave = new ArrayList<>();
        results.getViolations().forEach(rule ->
                rule.getNodes().forEach(node ->
                        toSave.add(new Violation(
                                page,
                                rule.getId(),
                                rule.getImpact(),
                                rule.getHelp(),
                                rule.getHelpUrl(),
                                node.getTarget().toString(),
                                node.getHtml()
                        ))
                )
        );
        violationRepository.saveAll(toSave);
    }

    @Transactional
    public void markDone(UUID jobId) {
        scanJobRepository.findById(jobId).orElseThrow().markDone();
    }

    @Transactional
    public void markFailed(UUID jobId, String message) {
        scanJobRepository.findById(jobId).orElseThrow().markFailed(message);
    }

    @Transactional
    public void recordFailure(UUID jobId, String message) {
        retryOrFail(scanJobRepository.findById(jobId).orElseThrow(), message);
    }

    @Transactional
    public void reapStaleRunningJobs(Duration timeout) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(timeout);
        scanJobRepository.findByStatusAndStartedAtBefore(ScanStatus.RUNNING, cutoff)
                .forEach(job -> retryOrFail(job, "stale: exceeded processing timeout"));
    }

    private void retryOrFail(ScanJob job, String message) {
        job.incrementAttempts();
        if (job.getAttempts() < MAX_ATTEMPTS) {
            job.markQueued();
        } else {
            job.markFailed(message);
        }
    }
}