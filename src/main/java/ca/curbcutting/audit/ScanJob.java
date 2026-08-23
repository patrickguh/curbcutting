package ca.curbcutting.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.time.Instant;
@Entity
@Table(name = "scan_jobs")
public class ScanJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "root_url", nullable = false, columnDefinition = "text")
    private String rootUrl;

    @Enumerated(EnumType.STRING)
    private ScanStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(columnDefinition = "text")
    private String interpretation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "is_example", nullable = false)
    private boolean isExample = false;

    @Column(name = "viewed_at")
    private OffsetDateTime viewedAt;

    protected ScanJob() { }          // required by JPA

    public ScanJob(String rootUrl) {
        this.rootUrl = rootUrl;
        this.status = ScanStatus.QUEUED;
    }

    public ScanJob(String rootUrl, User owner) {
        this.rootUrl = rootUrl;
        this.status = ScanStatus.QUEUED;
        this.owner = owner;
    }

    public UUID getId() { return id; }
    public String getRootUrl() { return rootUrl; }
    public User getOwner() { return owner; }
    public boolean isExample() { return isExample; }
    public OffsetDateTime getViewedAt() { return viewedAt; }

    public boolean isAnonymous() {
        return owner == null && !isExample;
    }

    public void markViewed() {
        this.viewedAt = OffsetDateTime.now();
    }

    /** Owner sees their own scans forever; anyone sees the curated example; an
     *  anonymous scan is viewable exactly once, then it's gone - by design,
     *  it was never saved to an account. */
    public boolean isViewableBy(User viewer) {
        if (isExample) {
            return true;
        }
        if (owner != null) {
            return viewer != null && owner.getId().equals(viewer.getId());
        }
        return viewedAt == null;
    }
    public ScanStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttempts() { return attempts; }
    public String getInterpretation() { return interpretation; }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
    }

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

    public void markQueued() {
        this.status = ScanStatus.QUEUED;
        this.startedAt = null;
    }

    public void incrementAttempts() {
        this.attempts++;
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

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }
}