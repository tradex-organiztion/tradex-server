package hello.tradexserver.repository;

import hello.tradexserver.domain.VerificationCode;
import hello.tradexserver.domain.enums.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findByPhoneNumberAndTypeAndVerifiedFalseAndExpiresAtAfter(
            String phoneNumber, VerificationType type, LocalDateTime now);

    Optional<VerificationCode> findByPhoneNumberAndCodeAndTypeAndVerifiedFalseAndExpiresAtAfter(
            String phoneNumber, String code, VerificationType type, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM VerificationCode v WHERE v.expiresAt < :now")
    void deleteExpiredCodes(LocalDateTime now);

    void deleteByPhoneNumberAndType(String phoneNumber, VerificationType type);
}
