package ca.curbcutting.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {
    List<PasswordResetCode> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
