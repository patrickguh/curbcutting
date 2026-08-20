package ca.curbcutting.audit;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScanService {

    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;

    public ScanService(ScanJobRepository scanJobRepository,
                       PageRepository pageRepository,
                       ViolationRepository violationRepository) {
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
    }

    @Transactional
    public ScanJob enqueue(String url) {
        return scanJobRepository.save(new ScanJob(url));
    }

    @Transactional
    public void markRunning(UUID jobId) {
        scanJobRepository.findById(jobId).orElseThrow().markRunning();
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
    public void storeResults(UUID jobId, AxeResults results) {
        ScanJob job = scanJobRepository.findById(jobId).orElseThrow();
        Page page = pageRepository.save(new Page(job, job.getRootUrl()));

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

        job.markDone();
    }

    @Transactional
    public void markFailed(UUID jobId, String message) {
        scanJobRepository.findById(jobId).orElseThrow().markFailed(message);
    }
}