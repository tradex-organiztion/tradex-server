package hello.tradexserver.repository;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.ExchangeName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeApiKeyRepository extends JpaRepository<ExchangeApiKey, Long> {

    List<ExchangeApiKey> findByUser(User user);

    List<ExchangeApiKey> findByUser_UserId(Long userId);

    Optional<ExchangeApiKey> findByUserAndExchangeName(User user, String exchangeName);

    boolean existsByUserAndExchangeName(User user, String exchangeName);

    /**
     * 활성화된 모든 API Key 조회 (isActive = true)
     */
    @Query("SELECT eak FROM ExchangeApiKey eak WHERE eak.isActive = true")
    List<ExchangeApiKey> findAllActive();

    /**
     * 특정 사용자의 활성화된 API Key만 조회
     */
    @Query("SELECT eak FROM ExchangeApiKey eak WHERE eak.user.id = :userId AND eak.isActive = true")
    List<ExchangeApiKey> findActiveByUserId(Long userId);

    /**
     * 사용자 + 거래소로 활성 API Key 조회
     */
    Optional<ExchangeApiKey> findByUserIdAndExchangeNameAndIsActiveTrue(Long userId, ExchangeName exchangeName);
}
