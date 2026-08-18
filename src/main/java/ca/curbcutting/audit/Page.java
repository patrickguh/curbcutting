package ca.curbcutting.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_job_id")
    private ScanJob scanJob;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "scanned_at")
    private OffsetDateTime scannedAt;

    protected Page() { }

    public Page(ScanJob scanJob, String url) {
        this.scanJob = scanJob;
        this.url = url;
        this.scannedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getUrl() { return url; }
    public OffsetDateTime getScannedAt() { return scannedAt; }
}