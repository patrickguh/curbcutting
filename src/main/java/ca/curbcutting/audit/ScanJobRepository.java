package ca.curbcutting.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ScanJobRepository extends JpaRepository<ScanJob, UUID> {

    Optional<ScanJob> findFirstByStatusOrderByCreatedAtAsc(ScanStatus status);
}

interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findByScanJobId(UUID scanJobId);
}

interface ViolationRepository extends JpaRepository<Violation, UUID> {
    List<Violation> findByPageId(UUID pageId);
}