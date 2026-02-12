package hello.tradexserver.service;

import hello.tradexserver.domain.Order;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.User;
import hello.tradexserver.domain.enums.OrderStatus;
import hello.tradexserver.dto.request.OrderRequest;
import hello.tradexserver.dto.response.OrderResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final OrderMappingService orderMappingService;

    /**
     * 오더 수동 추가 (포지션에 연결) + closed 포지션이면 재계산
     */
    public OrderResponse create(Long userId, Long positionId, OrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        Position position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        Order order = Order.builder()
                .user(user)
                .position(position)
                .exchangeName(position.getExchangeName())
                .exchangeOrderId("MANUAL-" + System.currentTimeMillis())
                .symbol(position.getSymbol())
                .side(request.getSide())
                .orderType(request.getOrderType())
                .positionEffect(request.getPositionEffect())
                .filledQuantity(request.getFilledQuantity())
                .filledPrice(request.getFilledPrice())
                .cumExecFee(request.getCumExecFee())
                .realizedPnl(request.getRealizedPnl())
                .status(OrderStatus.FILLED)
                .orderTime(request.getOrderTime())
                .fillTime(request.getFillTime())
                .build();

        orderRepository.save(order);
        log.info("[OrderService] 오더 수동 추가 - userId: {}, positionId: {}, orderId: {}",
                userId, positionId, order.getId());

        if (position.isClosed()) {
            orderMappingService.recalculatePosition(positionId);
        }

        return OrderResponse.from(order);
    }

    /**
     * 오더 수정 → closed 포지션이면 재계산
     */
    public OrderResponse update(Long userId, Long orderId, OrderRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.update(
                request.getFilledQuantity(), request.getFilledPrice(),
                request.getCumExecFee(), request.getRealizedPnl(),
                request.getOrderTime(), request.getFillTime()
        );

        orderRepository.save(order);
        log.info("[OrderService] 오더 수정 - userId: {}, orderId: {}", userId, orderId);

        if (order.getPosition() != null && order.getPosition().isClosed()) {
            orderMappingService.recalculatePosition(order.getPosition().getId());
        }

        return OrderResponse.from(order);
    }

    /**
     * 오더 삭제 → closed 포지션이면 재계산
     */
    public void delete(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Long positionId = order.getPosition() != null ? order.getPosition().getId() : null;
        boolean wasClosed = order.getPosition() != null && order.getPosition().isClosed();

        orderRepository.delete(order);
        log.info("[OrderService] 오더 삭제 - userId: {}, orderId: {}", userId, orderId);

        if (wasClosed && positionId != null) {
            orderMappingService.recalculatePosition(positionId);
        }
    }
}