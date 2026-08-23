package ca.curbcutting.audit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Scans run without an account are meant to be genuinely temporary, not just
 * hidden after being viewed once - this sweep actually deletes them (and,
 * via cascade, their pages/violations) so nothing lingers in the database.
 */
@Component
public class AnonymousScanCleanup {

    private static final Duration RETENTION = Duration.ofHours(1);

    private final ScanService scanService;

    public AnonymousScanCleanup(ScanService scanService) {
        this.scanService = scanService;
    }

    @Scheduled(fixedDelay = 600_000)
    public void purgeExpiredAnonymousScans() {
        scanService.purgeAnonymousScans(RETENTION);
    }
}
