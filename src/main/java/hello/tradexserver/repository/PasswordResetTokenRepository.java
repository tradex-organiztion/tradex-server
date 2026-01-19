package hello.tradexserver.repository;

import hello.tradexserver.domain.PasswordResetToken;
import hello.tradexserver.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, LocalDateTime now);

    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
