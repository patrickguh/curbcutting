package ca.curbcutting.audit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    public ScanViewController(ScanService scanService,
                              ScanJobRepository scanJobRepository,
                              PageRepository pageRepository,
                              ViolationRepository violationRepository) {
        this.scanService = scanService;
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
    }

    public record PageReport(Page page, List<Violation> violations) { }

    public record CategorySummary(String slug, String label, long count) { }

    @GetMapping("/")
    public String listScans(Model model) {
        List<ScanJob> jobs = scanJobRepository.findAll(); // TODO: paginate once the demo dataset grows past a screenful
        jobs.sort(Comparator.comparing(ScanJob::getCreatedAt).reversed());
        model.addAttribute("jobs", jobs);
        return "scans";
    }

    @PostMapping("/")
    public String submitScan(@RequestParam String url, RedirectAttributes redirectAttributes) {
        ScanJob job = scanService.enqueue(url);
        redirectAttributes.addFlashAttribute("submittedId", job.getId());
        return "redirect:/";
    }

    @GetMapping("/scans/{id}/report")
    public String scanReport(@PathVariable UUID id, Model model) {
        ScanJob job = scanJobRepository.findById(id).orElseThrow();
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
