package hello.tradexserver.repository;

import hello.tradexserver.domain.Subscription;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(User user);

    Optional<Subscription> findByUserId(Long userId);

    List<Subscription> findByNextBillingDateAndStatus(LocalDate nextBillingDate, SubscriptionStatus status);
}