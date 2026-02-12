package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.*;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPositionMappingIntegrationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PositionRepository positionRepository;

    @InjectMocks
    private OrderMappingService orderMappingService;

    private User user;
    private ExchangeApiKey apiKey;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        apiKey = ExchangeApiKey.builder()
                .user(user)
                .exchangeName(ExchangeName.BYBIT)
                .build();
    }

    // ===== OrderMappingService 로직 테스트 =====

    @Nested
    @DisplayName("단방향 모드(one-way) 매핑")
    class OneWayModeMapping {

        @Test
        @DisplayName("진입/청산 오더가 있으면 CLOSED_MAPPED")
        void ordersPresent_closedMapped() {
            LocalDateTime entry = LocalDateTime.now().minusHours(2);
            LocalDateTime exit = LocalDateTime.now().minusMinutes(10);

            Position position = buildClosingPosition("BTCUSDT", PositionSide.LONG, entry, exit);

            Order entryOrder = buildOrder("ord-001", "BTCUSDT", OrderSide.BUY, PositionEffect.OPEN, 0, entry.plusSeconds(1));
            Order exitOrder  = buildOrder("ord-002", "BTCUSDT", OrderSide.SELL, PositionEffect.CLOSE, 0, exit.minusSeconds(5));

            given(orderRepository.findOrdersForMapping(any(), any(), any(), any(), any()))
                    .willReturn(List.of(entryOrder, exitOrder));

            orderMappingService.mapOrdersToPosition(position);

            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED_MAPPED);
            verify(orderRepository).saveAll(argThat(orders ->
                    ((List<?>) orders).size() == 2
            ));
        }

        @Test
        @DisplayName("후보 오더 없으면 CLOSED_UNMAPPED")
        void noOrders_closedUnmapped() {
            Position position = buildClosingPosition("BTCUSDT", PositionSide.LONG,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(5));

            given(orderRepository.findOrdersForMapping(any(), any(), any(), any(), any()))
                    .willReturn(Collections.emptyList());

            orderMappingService.mapOrdersToPosition(position);

            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED_UNMAPPED);
        }

        @Test
        @DisplayName("positionIdx=0 오더만 있으면 one-way로 판단해 전부 매핑")
        void allOneWayIdx_allMapped() {
            LocalDateTime entry = LocalDateTime.now().minusHours(1);
            LocalDateTime exit = LocalDateTime.now().minusMinutes(5);
            Position position = buildClosingPosition("ETHUSDT", PositionSide.LONG, entry, exit);

            Order o1 = buildOrder("o1", "ETHUSDT", OrderSide.BUY,  PositionEffect.OPEN,  0, entry.plusSeconds(1));
            Order o2 = buildOrder("o2", "ETHUSDT", OrderSide.SELL, PositionEffect.CLOSE, 0, exit.minusSeconds(1));

            given(orderRepository.findOrdersForMapping(any(), any(), any(), any(), any()))
                    .willReturn(List.of(o1, o2));

            orderMappingService.mapOrdersToPosition(position);

            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED_MAPPED);
        }
    }

    @Nested
    @DisplayName("헷지 모드(hedge) 매핑")
    class HedgeModeMapping {

        @Test
        @DisplayName("LONG 포지션은 positionIdx=1 오더만 매핑, SHORT(idx=2) 제외")
        void longPosition_onlyIdx1Mapped() {
            LocalDateTime entry = LocalDateTime.now().minusHours(2);
            LocalDateTime exit  = LocalDateTime.now().minusMinutes(10);
            Position position = buildClosingPosition("BTCUSDT", PositionSide.LONG, entry, exit);

            Order longEntry  = buildOrder("long-entry",  "BTCUSDT", OrderSide.BUY,  PositionEffect.OPEN,  1, entry.plusSeconds(1));
            Order longExit   = buildOrder("long-exit",   "BTCUSDT", OrderSide.SELL, PositionEffect.CLOSE, 1, exit.minusSeconds(5));
            Order shortEntry = buildOrder("short-entry", "BTCUSDT", OrderSide.SELL, PositionEffect.OPEN,  2, entry.plusSeconds(2));
            Order shortExit  = buildOrder("short-exit",  "BTCUSDT", OrderSide.BUY,  PositionEffect.CLOSE, 2, exit.minusSeconds(3));

            given(orderRepository.findOrdersForMapping(any(), any(), any(), any(), any()))
                    .willReturn(List.of(longEntry, longExit, shortEntry, shortExit));

            orderMappingService.mapOrdersToPosition(position);

            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED_MAPPED);
            // SHORT 오더(idx=2)는 제외, LONG 오더(idx=1) 2건만 매핑
            verify(orderRepository).saveAll(argThat(orders ->
                    ((List<?>) orders).size() == 2
            ));
        }

        @Test
        @DisplayName("SHORT 포지션은 positionIdx=2 오더만 매핑, LONG(idx=1) 제외")
        void shortPosition_onlyIdx2Mapped() {
            LocalDateTime entry = LocalDateTime.now().minusHours(1);
            LocalDateTime exit  = LocalDateTime.now().minusMinutes(5);
            Position position = buildClosingPosition("BTCUSDT", PositionSide.SHORT, entry, exit);

            Order longEntry  = buildOrder("long-entry",  "BTCUSDT", OrderSide.BUY,  PositionEffect.OPEN,  1, entry.plusSeconds(1));
            Order shortEntry = buildOrder("short-entry", "BTCUSDT", OrderSide.SELL, PositionEffect.OPEN,  2, entry.plusSeconds(2));
            Order shortExit  = buildOrder("short-exit",  "BTCUSDT", OrderSide.BUY,  PositionEffect.CLOSE, 2, exit.minusSeconds(1));

            given(orderRepository.findOrdersForMapping(any(), any(), any(), any(), any()))
                    .willReturn(List.of(longEntry, shortEntry, shortExit));

            orderMappingService.mapOrdersToPosition(position);

            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED_MAPPED);
            verify(orderRepository).saveAll(argThat(orders ->
                    ((List<?>) orders).size() == 2
            ));
        }
    }

    // ===== 헬퍼 =====

    private Position buildClosingPosition(String symbol, PositionSide side,
                                          LocalDateTime entryTime, LocalDateTime exitTime) {
        return Position.builder()
                .user(user)
                .exchangeApiKey(apiKey)
                .exchangeName(ExchangeName.BYBIT)
                .symbol(symbol)
                .side(side)
                .avgEntryPrice(new BigDecimal("50000"))
                .totalSize(new BigDecimal("0.01"))
                .currentSize(BigDecimal.ZERO)
                .leverage(10)
                .realizedPnl(new BigDecimal("100"))
                .entryTime(entryTime)
                .exitTime(exitTime)
                .status(PositionStatus.CLOSING)
                .build();
    }

    private Order buildOrder(String orderId, String symbol, OrderSide side,
                              PositionEffect positionEffect, int positionIdx, LocalDateTime fillTime) {
        return Order.builder()
                .user(user)
                .exchangeApiKey(apiKey)
                .exchangeName(ExchangeName.BYBIT)
                .exchangeOrderId(orderId)
                .symbol(symbol)
                .side(side)
                .orderType(OrderType.MARKET)
                .positionEffect(positionEffect)
                .positionIdx(positionIdx)
                .filledQuantity(new BigDecimal("0.01"))
                .filledPrice(new BigDecimal("50000"))
                .status(OrderStatus.FILLED)
                .orderTime(fillTime.minusSeconds(1))
                .fillTime(fillTime)
                .build();
    }
}