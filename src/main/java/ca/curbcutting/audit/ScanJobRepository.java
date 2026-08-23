package ca.curbcutting.audit;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;


public interface ScanJobRepository extends JpaRepository<ScanJob, UUID> {

    @Query(value = """
            SELECT * FROM scan_jobs
            WHERE status = 'QUEUED'
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ScanJob> claimNextQueued();
    Optional<ScanJob> findFirstByStatusOrderByCreatedAtAsc(ScanStatus status);
    List<ScanJob> findByStatusAndStartedAtBefore(ScanStatus status, OffsetDateTime cutoff);
    List<ScanJob> findByOwnerId(UUID ownerId);
    Optional<ScanJob> findFirstByIsExampleTrue();
}

interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findByScanJobId(UUID scanJobId);
}

interface ViolationRepository extends JpaRepository<Violation, UUID> {
    List<Violation> findByPageId(UUID pageId);
}