package hello.tradexserver.repository;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeApiKeyRepository extends JpaRepository<ExchangeApiKey, Long> {

    List<ExchangeApiKey> findByUser(User user);

    Optional<ExchangeApiKey> findByUserAndExchangeName(User user, String exchangeName);

    boolean existsByUserAndExchangeName(User user, String exchangeName);
}
