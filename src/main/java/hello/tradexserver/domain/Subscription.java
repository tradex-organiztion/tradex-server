package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.SubscriptionPlan;
import hello.tradexserver.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String billingKey;

    private String customerKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private SubscriptionPlan plan = SubscriptionPlan.FREE;

    private LocalDate startDate;

    private LocalDate nextBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private String cancellationReason;

    private LocalDate cancelledAt;

    private String cardNumber;

    private String cardCompany;

    public void updatePlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public void updateBillingKey(String billingKey, String customerKey, String cardNumber, String cardCompany) {
        this.billingKey = billingKey;
        this.customerKey = customerKey;
        this.cardNumber = cardNumber;
        this.cardCompany = cardCompany;
    }

    public void updateNextBillingDate(LocalDate nextBillingDate) {
        this.nextBillingDate = nextBillingDate;
    }

    public void cancel(String reason) {
        this.status = SubscriptionStatus.CANCELED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDate.now();
    }

    public void reactivate() {
        this.status = SubscriptionStatus.ACTIVE;
        this.cancellationReason = null;
        this.cancelledAt = null;
    }
}