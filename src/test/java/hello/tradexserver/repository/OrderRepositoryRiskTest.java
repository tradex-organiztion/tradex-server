package hello.tradexserver.repository;

import hello.tradexserver.common.util.EncryptionUtil;
import hello.tradexserver.domain.*;
import hello.tradexserver.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderRepository.findOpenOrdersByPositionIds 전용 테스트
 *
 * 테스트 데이터:
 *   pos1 BYBIT BTCUSDT CLOSED
 *     order1 OPEN  fillTime=T-5h (진입)
 *     order2 CLOSE fillTime=T-1h (청산)
 *   pos2 BYBIT ETHUSDT CLOSED
 *     order3 OPEN fillTime=T-3h (첫 번째 진입)
 *     order4 OPEN fillTime=T-2h (두 번째 진입, 물타기 시나리오)
 *   pos3 BYBIT SOLUSDT CLOSED
 *     order5 OPEN fillTime=T-4h (진입)
 *
 * @Import(EncryptionUtil.class) 이유:
 *   ExchangeApiKey의 apiKey/apiSecret 필드에 @Convert(converter = EncryptedStringConverter.class)가 적용됨.
 *   EncryptedStringConverter는 EncryptionUtil.encrypt/decrypt(static)을 호출하는데,
 *   EncryptionUtil은 @Component이므로 @DataJpaTest 컨텍스트에서 자동 등록되지 않음.
 *   @Import로 명시적으로 등록하면 @Value("${encryption.secret-key}")가 주입되어 암호화가 정상 동작함.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EncryptionUtil.class)
class OrderRepositoryRiskTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OrderRepository orderRepository;

    private User user;
    private ExchangeApiKey apiKey;
    private Position pos1, pos2, pos3;
    private Order order1, order2, order3, order4, order5;
    private final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("order-risk-test@test.com")
                .username("orderRiskTestUser")
                .build());

        // ExchangeApiKey — EncryptedStringConverter가 동작해야 persist 가능
        apiKey = em.persist(ExchangeApiKey.builder()
                .user(user)
                .exchangeName(ExchangeName.BYBIT)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .build());

        // ── pos1: BTCUSDT, 진입 오더 1건 + 청산 오더 1건 ──────────────────
        pos1 = em.persist(Position.builder()
                .user(user).exchangeApiKey(apiKey)
                .symbol("BTCUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("40000"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("0.1")).leverage(10)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(NOW.minusHours(6)).exitTime(NOW.minusHours(1))
                .status(PositionStatus.CLOSED).build());

        order1 = em.persist(Order.builder()
                .user(user).exchangeApiKey(apiKey).position(pos1)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId("order-btc-open-001")
                .symbol("BTCUSDT").side(OrderSide.BUY).orderType(OrderType.MARKET)
                .positionEffect(PositionEffect.OPEN)
                .filledQuantity(new BigDecimal("0.1")).filledPrice(new BigDecimal("40000"))
                .orderTime(NOW.minusHours(6)).fillTime(NOW.minusHours(5))
                .build());

        order2 = em.persist(Order.builder()
                .user(user).exchangeApiKey(apiKey).position(pos1)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId("order-btc-close-001")
                .symbol("BTCUSDT").side(OrderSide.SELL).orderType(OrderType.MARKET)
                .positionEffect(PositionEffect.CLOSE)
                .filledQuantity(new BigDecimal("0.1")).filledPrice(new BigDecimal("41000"))
                .orderTime(NOW.minusHours(2)).fillTime(NOW.minusHours(1))
                .build());

        // ── pos2: ETHUSDT, 진입 오더 2건 (물타기 시나리오) ──────────────────
        pos2 = em.persist(Position.builder()
                .user(user).exchangeApiKey(apiKey)
                .symbol("ETHUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("2500"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("1")).leverage(5)
                .realizedPnl(new BigDecimal("-50"))
                .entryTime(NOW.minusHours(4)).exitTime(NOW.minusHours(1))
                .status(PositionStatus.CLOSED).build());

        order3 = em.persist(Order.builder()
                .user(user).exchangeApiKey(apiKey).position(pos2)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId("order-eth-open-001")
                .symbol("ETHUSDT").side(OrderSide.BUY).orderType(OrderType.MARKET)
                .positionEffect(PositionEffect.OPEN)
                .filledQuantity(new BigDecimal("0.5")).filledPrice(new BigDecimal("2600"))
                .orderTime(NOW.minusHours(4)).fillTime(NOW.minusHours(3))
                .build());

        order4 = em.persist(Order.builder()
                .user(user).exchangeApiKey(apiKey).position(pos2)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId("order-eth-open-002")
                .symbol("ETHUSDT").side(OrderSide.BUY).orderType(OrderType.MARKET)
                .positionEffect(PositionEffect.OPEN)
                .filledQuantity(new BigDecimal("0.5")).filledPrice(new BigDecimal("2400"))
                .orderTime(NOW.minusHours(3)).fillTime(NOW.minusHours(2))
                .build());

        // ── pos3: SOLUSDT, 진입 오더 1건 ─────────────────────────────────────
        pos3 = em.persist(Position.builder()
                .user(user).exchangeApiKey(apiKey)
                .symbol("SOLUSDT").side(PositionSide.LONG)
                .exchangeName(ExchangeName.BYBIT)
                .avgEntryPrice(new BigDecimal("100"))
                .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("10")).leverage(3)
                .realizedPnl(new BigDecimal("30"))
                .entryTime(NOW.minusHours(5)).exitTime(NOW.minusHours(1))
                .status(PositionStatus.CLOSED).build());

        order5 = em.persist(Order.builder()
                .user(user).exchangeApiKey(apiKey).position(pos3)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId("order-sol-open-001")
                .symbol("SOLUSDT").side(OrderSide.BUY).orderType(OrderType.MARKET)
                .positionEffect(PositionEffect.OPEN)
                .filledQuantity(new BigDecimal("10")).filledPrice(new BigDecimal("100"))
                .orderTime(NOW.minusHours(5)).fillTime(NOW.minusHours(4))
                .build());

        em.flush();
        em.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // OPEN/CLOSE 필터링
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OPEN/CLOSE 필터링")
    class PositionEffectFilter {

        @Test
        @DisplayName("OPEN positionEffect 오더만 반환 — CLOSE 오더 제외")
        void OPEN_오더만_반환() {
            // pos1: order1(OPEN) + order2(CLOSE) → OPEN만 반환
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId()));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPositionEffect()).isEqualTo(PositionEffect.OPEN);
            assertThat(result.get(0).getExchangeOrderId()).isEqualTo("order-btc-open-001");
        }

        @Test
        @DisplayName("CLOSE 오더만 있는 포지션은 빈 결과 반환")
        void CLOSE_오더만_있으면_빈결과() {
            // CLOSE 오더만 있는 별도 포지션 생성
            Position closeOnlyPos = em.persist(Position.builder()
                    .user(user).exchangeApiKey(apiKey)
                    .symbol("BNBUSDT").side(PositionSide.LONG)
                    .exchangeName(ExchangeName.BYBIT)
                    .avgEntryPrice(new BigDecimal("300"))
                    .currentSize(BigDecimal.ZERO).closedSize(new BigDecimal("1")).leverage(5)
                    .realizedPnl(new BigDecimal("20"))
                    .entryTime(NOW.minusHours(3)).exitTime(NOW.minusHours(1))
                    .status(PositionStatus.CLOSED).build());

            em.persist(Order.builder()
                    .user(user).exchangeApiKey(apiKey).position(closeOnlyPos)
                    .exchangeName(ExchangeName.BYBIT)
                    .exchangeOrderId("order-bnb-close-only")
                    .symbol("BNBUSDT").side(OrderSide.SELL).orderType(OrderType.MARKET)
                    .positionEffect(PositionEffect.CLOSE)
                    .filledQuantity(new BigDecimal("1")).filledPrice(new BigDecimal("320"))
                    .orderTime(NOW.minusHours(2)).fillTime(NOW.minusHours(1))
                    .build());
            em.flush();
            em.clear();

            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(closeOnlyPos.getId()));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("복수 포지션 조회 시 모든 CLOSE 오더 제외")
        void 복수_포지션_CLOSE_오더_제외() {
            // pos1: order1(OPEN), order2(CLOSE) — pos2: order3(OPEN), order4(OPEN)
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId(), pos2.getId()));

            assertThat(result).allMatch(o -> o.getPositionEffect() == PositionEffect.OPEN);
            assertThat(result).noneMatch(o -> "order-btc-close-001".equals(o.getExchangeOrderId()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 복수 포지션 bulk 조회
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("복수 포지션 bulk 조회")
    class BulkFetch {

        @Test
        @DisplayName("두 포지션 ID로 조회 시 각 포지션의 OPEN 오더 합산 반환")
        void 두_포지션_bulk_조회() {
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId(), pos2.getId()));

            // order1(pos1 OPEN) + order3(pos2 OPEN) + order4(pos2 OPEN) = 3건
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(o -> o.getPositionEffect() == PositionEffect.OPEN);
        }

        @Test
        @DisplayName("세 포지션 ID로 조회 시 전체 OPEN 오더 반환")
        void 세_포지션_bulk_조회() {
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId(), pos2.getId(), pos3.getId()));

            // order1 + order3 + order4 + order5 = 4건 (order2 CLOSE 제외)
            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("목록에 없는 포지션의 오더는 포함되지 않음 — 포지션 격리")
        void 포지션_격리() {
            // pos1, pos2만 조회 → pos3의 order5는 포함되면 안 됨
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId(), pos2.getId()));

            assertThat(result).noneMatch(o -> o.getPosition().getId().equals(pos3.getId()));
            assertThat(result).noneMatch(o -> "order-sol-open-001".equals(o.getExchangeOrderId()));
        }

        @Test
        @DisplayName("단일 포지션 조회 시 해당 포지션의 OPEN 오더만 반환")
        void 단일_포지션_조회() {
            // pos2: order3(OPEN), order4(OPEN) 2건
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos2.getId()));

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(o -> o.getPosition().getId().equals(pos2.getId()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fillTime ASC 정렬
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fillTime ASC 정렬")
    class FillTimeOrder {

        @Test
        @DisplayName("전체 OPEN 오더 fillTime 오름차순 정렬 확인")
        void fillTime_asc_정렬() {
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos1.getId(), pos2.getId(), pos3.getId()));

            // 기대 순서: order1(T-5h) → order5(T-4h) → order3(T-3h) → order4(T-2h)
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).getFillTime())
                        .isBeforeOrEqualTo(result.get(i + 1).getFillTime());
            }
        }

        @Test
        @DisplayName("단일 포지션의 복수 OPEN 오더 — fillTime ASC 정렬")
        void 단일_포지션_복수_오더_정렬() {
            // pos2: order3(T-3h) → order4(T-2h) 순서
            List<Order> result = orderRepository
                    .findOpenOrdersByPositionIds(List.of(pos2.getId()));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getExchangeOrderId()).isEqualTo("order-eth-open-001"); // T-3h
            assertThat(result.get(1).getExchangeOrderId()).isEqualTo("order-eth-open-002"); // T-2h
        }
    }
}
