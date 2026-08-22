package ca.curbcutting.audit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class ScanWorker {

    private static final Duration STALE_TIMEOUT = Duration.ofMinutes(5);

    private final ScanJobRepository scanJobRepository;
    private final ScanService scanService;
    private static final Logger log = LoggerFactory.getLogger(ScanWorker.class);

    public ScanWorker(ScanJobRepository scanJobRepository, ScanService scanService) {
        this.scanJobRepository = scanJobRepository;
        this.scanService = scanService;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollOnce() {
        scanService.claimNextJob().ifPresent(job -> {
            try {
                for (String url : scanService.discoverPages(job.getRootUrl())) {
                    var results = scanService.runAxeOn(url);
                    scanService.storeResults(job.getId(), url, results);
                }
                scanService.markDone(job.getId());
            } catch (Exception e) {
                log.warn("Scan job {} failed: {}", job.getId(), e.getMessage());
                scanService.recordFailure(job.getId(), e.getMessage());
            }
        });
    }

    @Scheduled(fixedDelay = 30000)
    public void reapStaleJobs() {
        scanService.reapStaleRunningJobs(STALE_TIMEOUT);
    }
}