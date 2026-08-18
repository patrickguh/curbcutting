package ca.curbcutting.audit;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class ScanController {

    private final ScanService scanService;
    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;

    public ScanController(ScanService scanService,
                          ScanJobRepository scanJobRepository,
                          PageRepository pageRepository,
                          ViolationRepository violationRepository) {
        this.scanService = scanService;
        this.scanJobRepository = scanJobRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
    }

    @PostMapping("/scans")
    public UUID createScan(@RequestParam String url) {
        return scanService.scanAndStore(url);
    }

    @GetMapping("/scans/{id}")
    public ScanJob getScan(@PathVariable UUID id) {
        return scanJobRepository.findById(id).orElseThrow();
    }

    @GetMapping("/scans/{id}/violations")
    public List<Violation> getViolations(@PathVariable UUID id) {
        return pageRepository.findByScanJobId(id).stream()
                .flatMap(page -> violationRepository.findByPageId(page.getId()).stream())
                .toList();
    }
}