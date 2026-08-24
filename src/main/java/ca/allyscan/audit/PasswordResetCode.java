package ca.allyscan.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    private String code;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PasswordResetCode() { }

    public PasswordResetCode(User user, String code, OffsetDateTime expiresAt) {
        this.user = user;
        this.code = code;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getCode() { return code; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }

    public boolean isValid(String candidate) {
        return !used && code.equals(candidate) && expiresAt.isAfter(OffsetDateTime.now());
    }

    public void markUsed() {
        this.used = true;
    }
}
