package ca.curbcutting.audit;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class ScanViewController {

    private final ScanService scanService;
    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final UserRepository userRepository;

    public ScanViewController(ScanService scanService,
                              ScanJobRepository scanJobRepository,
                              PageRepository pageRepository,
                              ViolationRepository violationRepository,
                              UserRepository userRepository) {
        this.scanService = scanService;
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
        this.userRepository = userRepository;
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    public record PageReport(Page page, List<Violation> violations) { }

    public record CategorySummary(String slug, String label, long count) { }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        scanJobRepository.findFirstByIsExampleTrue().ifPresent(job -> model.addAttribute("exampleJob", job));

        User user = currentUser(authentication);
        if (user != null) {
            List<ScanJob> jobs = scanJobRepository.findByOwnerId(user.getId());
            jobs.sort(Comparator.comparing(ScanJob::getCreatedAt).reversed());
            model.addAttribute("jobs", jobs);
        }
        return "scans";
    }

    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "how-it-works";
    }

    @PostMapping("/guest-scan")
    public String guestScan(@RequestParam String url) {
        ScanJob job = scanService.enqueue(url);
        return "redirect:/scans/" + job.getId() + "/report";
    }

    @GetMapping("/scans/{id}/report")
    public String scanReport(@PathVariable UUID id, Authentication authentication, Model model) {
        ScanJob job = scanJobRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User viewer = currentUser(authentication);
        if (!job.isViewableBy(viewer)) {
            if (job.isAnonymous()) {
                // already shown once - that's expected, not an access violation
                return "scan-expired";
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        boolean isTerminal = job.getStatus() == ScanStatus.DONE || job.getStatus() == ScanStatus.FAILED;
        if (job.isAnonymous() && isTerminal) {
            scanService.recordAnonymousView(id);
        }

        List<PageReport> pageReports = pageRepository.findByScanJobId(id).stream()
                .map(page -> new PageReport(page, violationRepository.findByPageId(page.getId())))
                .toList();

        int totalViolations = pageReports.stream().mapToInt(pr -> pr.violations().size()).sum();

        List<CategorySummary> categorySummaries = pageReports.stream()
                .flatMap(pr -> pr.violations().stream())
                .collect(Collectors.groupingBy(Violation::getCategory, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new CategorySummary(e.getKey().getSlug(), e.getKey().getLabel(), e.getValue()))
                .sorted(Comparator.comparingLong(CategorySummary::count).reversed())
                .toList();

        model.addAttribute("job", job);
        model.addAttribute("pageReports", pageReports);
        model.addAttribute("totalViolations", totalViolations);
        model.addAttribute("categorySummaries", categorySummaries);
        return "scan-detail";
    }
}
