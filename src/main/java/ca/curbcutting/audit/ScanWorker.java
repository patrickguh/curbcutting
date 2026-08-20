package ca.curbcutting.audit;

import com.deque.html.axecore.results.AxeResults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class ScanWorker {

    private final ScanJobRepository scanJobRepository;
    private final ScanService scanService;
    private static final Logger log = LoggerFactory.getLogger(ScanWorker.class);

    public ScanWorker(ScanJobRepository scanJobRepository, ScanService scanService) {
        this.scanJobRepository = scanJobRepository;
        this.scanService = scanService;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollOnce() {
        Optional<ScanJob> next =
                scanJobRepository.findFirstByStatusOrderByCreatedAtAsc(ScanStatus.QUEUED);

        if (next.isEmpty()) return;

        UUID id = next.get().getId();
        String url = next.get().getRootUrl();

        scanService.markRunning(id);
        try {
            AxeResults results = scanService.runAxeOn(url);
            scanService.storeResults(id, results);
        } catch (Exception e) {
            scanService.markFailed(id, e.toString());
        }

    }
}