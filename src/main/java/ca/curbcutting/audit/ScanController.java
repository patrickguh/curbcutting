package ca.curbcutting.audit;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class ScanController {

    private final ScanService scanService;
    private final ScanJobRepository scanJobRepository;
    private final PageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final UserRepository userRepository;

    public ScanController(ScanService scanService,
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

    private ScanJob requireViewable(UUID id, Authentication authentication) {
        ScanJob job = scanJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!job.isViewableBy(currentUser(authentication))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return job;
    }

    @PostMapping("/scans")
    public ScanJob create(@RequestParam String url, Authentication authentication) {
        User owner = currentUser(authentication);
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return scanService.enqueue(url, owner);
    }

    @GetMapping("/scans")
    public List<ScanJob> listScans(Authentication authentication) {
        User owner = currentUser(authentication);
        if (owner == null) {
            return List.of();
        }
        return scanJobRepository.findByOwnerId(owner.getId());
    }

    @GetMapping("/scans/{id}")
    public ScanJob getScan(@PathVariable UUID id, Authentication authentication) {
        return requireViewable(id, authentication);
    }

    @GetMapping("/scans/{id}/violations")
    public List<Violation> getViolations(@PathVariable UUID id, Authentication authentication) {
        requireViewable(id, authentication);
        return pageRepository.findByScanJobId(id).stream()
                .flatMap(page -> violationRepository.findByPageId(page.getId()).stream())
                .toList();
    }
}
