package hello.tradexserver.domain;

import hello.tradexserver.domain.enums.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_histories")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SubscriptionPlan plan;

    private int amount;

    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentStatus status;

    private String paymentKey;

    private String orderId;
}