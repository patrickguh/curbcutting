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
    public UUID scanAndStore(String url) {
        ScanJob job = scanJobRepository.save(new ScanJob(url));

        try {
            job.markRunning();

            AxeResults results = runAxeOn(url);

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

            job.markDone();

        } catch (Exception e) {
            job.markFailed(e.getMessage());
        }

        return job.getId();
    }

    private AxeResults runAxeOn(String url) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            com.microsoft.playwright.Page browserPage = browser.newPage();

            browserPage.navigate(url);

            AxeResults results = new AxeBuilder(browserPage).analyze();

            browser.close();
            return results;
        }
    }
}