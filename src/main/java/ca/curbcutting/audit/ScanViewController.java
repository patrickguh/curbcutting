package ca.curbcutting.audit;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class ScanViewController {

    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final UserRepository userRepository;

    public ScanViewController(ScanJobRepository scanJobRepository,
                              PageRepository pageRepository,
                              ViolationRepository violationRepository,
                              UserRepository userRepository) {
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
    public String home(Model model) {
        scanJobRepository.findFirstByIsExampleTrue().ifPresent(job -> model.addAttribute("exampleJob", job));
        return "scans";
    }

    @GetMapping("/scans/{id}/report")
    public String scanReport(@PathVariable UUID id, Authentication authentication, Model model) {
        ScanJob job = scanJobRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User viewer = currentUser(authentication);
        boolean isOwner = job.getOwner() != null && viewer != null && job.getOwner().getId().equals(viewer.getId());
        if (!job.isExample() && !isOwner) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
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
