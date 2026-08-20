package ca.curbcutting.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scan_jobs")
public class ScanJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "root_url", nullable = false, columnDefinition = "text")
    private String rootUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ScanStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected ScanJob() { }          // required by JPA

    public ScanJob(String rootUrl) {
        this.rootUrl = rootUrl;
        this.status = ScanStatus.QUEUED;
    }

    public UUID getId() { return id; }
    public String getRootUrl() { return rootUrl; }
    public ScanStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public String getErrorMessage() { return errorMessage; }

    public void setRootUrl(String rootUrl) {
        this.rootUrl = rootUrl;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void markRunning() {
        this.status = ScanStatus.RUNNING;
        this.startedAt = OffsetDateTime.now();
    }

    public void markDone() {
        this.status = ScanStatus.DONE;
        this.finishedAt = OffsetDateTime.now();
    }

    public void markFailed(String message) {
        this.status = ScanStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = OffsetDateTime.now();
    }
}