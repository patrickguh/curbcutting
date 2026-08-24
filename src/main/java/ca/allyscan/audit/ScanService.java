package ca.allyscan.audit;

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
import ca.allyscan.audit.ScanStatus;

@Service
public class ScanService {

    private static final int MAX_ATTEMPTS = 3;

    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final SiteCrawler siteCrawler;
    private final ViolationInterpreter violationInterpreter;

    public ScanService(ScanJobRepository scanJobRepository,
                       PageRepository pageRepository,
                       ViolationRepository violationRepository,
                       SiteCrawler siteCrawler,
                       ViolationInterpreter violationInterpreter) {
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
        this.siteCrawler = siteCrawler;
        this.violationInterpreter = violationInterpreter;
    }

    @Transactional
    public ScanJob enqueue(String url) {
        return scanJobRepository.save(new ScanJob(url));
    }

    @Transactional
    public ScanJob enqueue(String url, User owner) {
        return scanJobRepository.save(new ScanJob(url, owner));
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

    public PageScanResult runAxeOn(String url) {          // was private
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            com.microsoft.playwright.Page browserPage = browser.newPage();
            browserPage.navigate(url);
            String title = browserPage.title();
            AxeResults results = new AxeBuilder(browserPage).analyze();
            browser.close();
            return new PageScanResult(title, results);
        }
    }

    @Transactional
    public void storeResults(UUID jobId, String url, PageScanResult scanResult) {
        ScanJob job = scanJobRepository.findById(jobId).orElseThrow();
        Page page = pageRepository.save(new Page(job, url, scanResult.title()));

        List<Violation> toSave = new ArrayList<>();
        scanResult.axeResults().getViolations().forEach(rule ->
                rule.getNodes().forEach(node ->
                        toSave.add(new Violation(
                                page,
                                rule.getId(),
                                rule.getImpact(),
                                rule.getHelp(),
                                rule.getHelpUrl(),
                                node.getTarget().toString(),
                                node.getHtml(),
                                String.join(",", rule.getTags())
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
    public void interpretResults(UUID jobId) {
        ScanJob job = scanJobRepository.findById(jobId).orElseThrow();
        List<Page> pages = pageRepository.findByScanJobId(jobId);

        StringBuilder report = new StringBuilder();
        report.append("Scan of ").append(job.getRootUrl()).append("\n\n");
        boolean anyViolations = false;
        for (Page page : pages) {
            List<Violation> violations = violationRepository.findByPageId(page.getId());
            if (violations.isEmpty()) {
                continue;
            }
            anyViolations = true;
            String pageLabel = (page.getTitle() != null && !page.getTitle().isBlank())
                    ? page.getTitle() + " (" + page.getUrl() + ")"
                    : page.getUrl();
            report.append("Page: ").append(pageLabel).append("\n");
            for (Violation v : violations) {
                report.append("- [").append(v.getImpact()).append("] ")
                        .append(v.getCategory().getLabel()).append(" — ")
                        .append(v.getRuleId()).append(": ").append(v.getHelpText()).append("\n");
            }
            report.append("\n");
        }

        String interpretation = anyViolations
                ? violationInterpreter.interpret(report.toString())
                : "No accessibility violations were detected across " + pages.size() + " page(s) scanned.";

        job.setInterpretation(interpretation);
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

    @Transactional
    public void recordAnonymousView(UUID jobId) {
        ScanJob job = scanJobRepository.findById(jobId).orElseThrow();
        if (job.isAnonymous() && job.getViewedAt() == null) {
            job.markViewed();
        }
    }

    @Transactional
    public void purgeAnonymousScans(Duration retention) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        scanJobRepository.findByOwnerIsNullAndIsExampleFalse().forEach(job -> {
            boolean alreadyShown = job.getViewedAt() != null;
            boolean abandoned = job.getCreatedAt().isBefore(cutoff);
            if (alreadyShown || abandoned) {
                scanJobRepository.delete(job);
            }
        });
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