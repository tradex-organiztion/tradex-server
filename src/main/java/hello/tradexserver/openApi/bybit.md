# 바이비트 Order-Position 동기화 시스템 설계

## 전체 아키텍처 개요
```
[실시간 포지션 감지] → WebSocket Position Stream
    ↓
[Order 수집] → REST API 배치 (5분마다)
    ↓
[Order 저장] → DB (Position 연결 안 함)
    ↓
[Position Close 감지] → WebSocket Close Event
    ↓
[Order-Position 매핑] → 비동기 매핑 작업
    ↓
[매핑 완료] → Position 상태 업데이트
```

## 1. 핵심 프로세스

### Phase 1: Order 수집 및 저장
- **목적**: Order 데이터를 지속적으로 수집하여 DB에 저장
- **방식**: REST API 배치 (5분마다)
- **저장**: Position 연결 없이 Order만 독립적으로 저장
- **중복 방지**: exchangeOrderId 기준으로 기존 DB 데이터와 비교 후 신규만 저장

### Phase 2: 실시간 포지션 모니터링
- **목적**: 사용자에게 실시간 포지션 상태 제공
- **방식**: WebSocket Position Stream 구독
- **동작**:
    - Position Open/Update → 즉시 프론트엔드 전송 (프론트로 전송하는 건 지금 단계에서 구현은 안 함)
    - DB에는 임시 상태(OPEN)로 저장
    - 이 시점에는 Order 매핑하지 않음

### Phase 3: Position Close 시 Order 매핑 (핵심!)
- **트리거**: WebSocket에서 Position Close 감지
- **동작**:
    1. Position 상태를 CLOSING으로 변경
    2. 비동기로 Order 매핑 작업 시작
    3. 매핑 조건으로 관련 Order 조회:
        - exchangeApiKey.id 일치 (필수!)
        - symbol 일치
        - 헷지 모드에서는 position side 일치 여부 확인
        - orderTime이 entryTime ~ closeTime 범위
        - user.id 일치
    4. reduceOnly 필드로 진입/청산 Order 구분
    5. Order.position 필드에 Position 연결
    6. Position 상태를 CLOSED_MAPPED로 변경
    7. 실패 시 CLOSED_UNMAPPED 상태로 저장 후 재시도 대기

## 2. 역할 분리

### 거래소별 구현 (ExchangeOrderService)
- **책임**: 거래소 API 응답 → Order 엔티티 변환
- **구현체**:
    - BybitOrderService: 바이비트 전용
    - BinanceOrderService: 바이낸스 전용 (향후)

- **주요 메서드**:
    - `fetchAndConvertOrders()`: API 호출 및 Order 엔티티 변환
    - 거래소별 필드 매핑 처리

- **변환 예시 (바이비트)**:
    - `orderId` → `exchangeOrderId`
    - `side` (Buy/Sell) → `OrderSide` (BUY/SELL)
    - `reduceOnly` (true/false) → `PositionEffect` (CLOSE/OPEN)
    - `cumExecQty` → `filledQuantity`
    - `avgPrice` → `filledPrice`

### 공통 로직 (OrderMappingService)
- **책임**: 이미 표준화된 Order 엔티티를 Position에 매핑
- **사용**: 모든 거래소 공통으로 사용
- **이유**: Order 엔티티는 이미 거래소 무관한 표준 형식

- **주요 메서드**:
    - `mapOrdersToPosition(Position)`: Order-Position 매핑

- **매핑 알고리즘**:
    1. exchangeApiKey로 필터링 (같은 계정의 Order만)
    2. symbol + timeRange로 후보 Order 조회
    3. positionEffect로 진입/청산 구분
    4. Order 리스트를 Position에 연결

## 3. 데이터 모델

### Order 엔티티 (공통 표준)
```java
@Entity
@Table(
    name = "orders",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_exchange_order",
        columnNames = {"exchange_name", "exchange_order_id"}
    )
)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;  // null 가능 (매핑 전)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_api_key_id", nullable = false)
    private ExchangeApiKey exchangeApiKey;  // 어떤 API Key로 거래했는지
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeName exchangeName;
    
    @Column(nullable = false)
    private String exchangeOrderId;  // 거래소의 Order ID
    
    @Column(nullable = false)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;  // BUY, SELL
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;  // MARKET, LIMIT
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionEffect positionEffect;  // OPEN, CLOSE
    
    @Column(precision = 20, scale = 8)
    private BigDecimal filledQuantity;
    
    @Column(precision = 20, scale = 8)
    private BigDecimal filledPrice;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @Column(nullable = false)
    private LocalDateTime orderTime;
    
    private LocalDateTime fillTime;
    
    // 거래소별 추가 필드
    private Integer positionIdx;  // hedge mode 구분용
    private String orderLinkId;   // 사용자 지정 ID
    @Column(precision = 20, scale = 8)
    private BigDecimal cumExecFee;  // 수수료
    private Boolean reduceOnly;   // 원본 데이터 보존
}
```

### Position 엔티티
```java
@Entity
@Table(name = "positions")
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_api_key_id", nullable = false)
    private ExchangeApiKey exchangeApiKey;  // 어떤 API Key로 거래했는지
    
    @Column(nullable = false)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;  // OPEN, CLOSING, CLOSED_MAPPED, CLOSED_UNMAPPED
    
    @Column(nullable = false)
    private LocalDateTime entryTime;
    
    private LocalDateTime closeTime;
    
    // ... 기타 포지션 관련 필드들
    
    // 편의 메서드
    public ExchangeName getExchangeName() {
        return exchangeApiKey.getExchangeName();
    }
}

enum PositionStatus {
    OPEN,           // WebSocket으로 감지, 매핑 전
    CLOSING,        // Close 감지, 매핑 진행 중
    CLOSED_MAPPED,  // 매핑 완료
    CLOSED_UNMAPPED // 매핑 실패 (재시도 대기)
}
```

## 4. 바이비트 Order History API 매핑

### API 파라미터
```
GET /v5/order/history
- category: "linear" (USDT 무기한)
- symbol: 심볼
- startTime: 조회 시작 시간
- endTime: 조회 종료 시간
- limit: 50
```

### 응답 필드 → Order 엔티티 매핑
```
orderId          → exchangeOrderId
symbol           → symbol
side (Buy/Sell)  → side (BUY/SELL로 변환)
orderType        → orderType (MARKET/LIMIT로 변환)
cumExecQty       → filledQuantity
avgPrice         → filledPrice
orderStatus      → status (변환)
createdTime      → orderTime (밀리초 → LocalDateTime 변환)
updatedTime      → fillTime (Filled 상태일 때만)
reduceOnly       → positionEffect 판단 (true=CLOSE, false=OPEN)
                 → reduceOnly 원본값도 저장
positionIdx      → positionIdx (hedge mode 구분)
orderLinkId      → orderLinkId
cumExecFee       → cumExecFee
```

### 저장 필터 조건
```
저장 대상:
- orderStatus = "Filled" (완전 체결)
- orderStatus = "Cancelled" AND cumExecQty > 0 (부분 체결 후 취소)

제외 대상:
- orderStatus = "New" (미체결)
- orderStatus = "PartiallyFilled" (진행중)
- orderStatus = "Cancelled" AND cumExecQty = 0 (미체결 취소)
- orderStatus = "Rejected" (거부됨)
```

## 5. 컴포넌트 구조 (참고용. 현재 프로젝트 구조 고려해서 하면 됨.)
```
com.tradex.exchange
├── service
│   ├── ExchangeOrderService.java (인터페이스)
│   ├── BybitOrderService.java (구현체)
│   └── OrderMappingService.java (공통 매핑)
├── websocket
│   ├── BybitWebSocketService.java
│   └── BybitWebSocketClient.java
├── api
│   ├── BybitApiClient.java
│   └── dto
│       ├── BybitOrderResponse.java
│       └── BybitOrderItem.java
├── event
│   ├── PositionCloseEvent.java
│   └── OrderSyncEventListener.java
└── mapper
    └── BybitOrderMapper.java
```

## 6. 핵심 메서드 시그니처

### ExchangeOrderService (인터페이스)
```java
public interface ExchangeOrderService {
    // Order 조회 및 변환 (거래소별 구현)
    List<Order> fetchAndConvertOrders(
        ExchangeApiKey apiKey,
        String symbol,
        LocalDateTime startTime,
        LocalDateTime endTime
    );
    
    // 저장 필터 체크 (거래소별 구현)
    boolean shouldSaveOrder(Object rawOrderData);
}
```

### BybitOrderService (구현체)
```java
@Service
public class BybitOrderService implements ExchangeOrderService {
    
    @Override
    public List<Order> fetchAndConvertOrders(
        ExchangeApiKey apiKey,
        String symbol,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        // 1. Bybit API 호출
        // 2. 응답 파싱
        // 3. 필터링 (shouldSaveOrder)
        // 4. Order 엔티티 변환
        // 5. 반환
    }
    
    @Override
    public boolean shouldSaveOrder(Object rawOrderData) {
        BybitOrderItem item = (BybitOrderItem) rawOrderData;
        String status = item.getOrderStatus();
        BigDecimal execQty = new BigDecimal(item.getCumExecQty());
        
        if ("Filled".equals(status)) return true;
        if ("Cancelled".equals(status) && execQty.compareTo(BigDecimal.ZERO) > 0) return true;
        
        return false;
    }
}
```

### OrderMappingService (공통)
```java
@Service
public class OrderMappingService {
    
    @Async
    public void mapOrdersToPosition(Position position) {
        try {
            // 1. Position 상태 → CLOSING
            position.setStatus(PositionStatus.CLOSING);
            positionRepository.save(position);
            
            // 2. 관련 Order 조회 (핵심 조건!)
            List<Order> orders = orderRepository.findOrders(
                position.getUser().getId(),
                position.getExchangeApiKey().getId(),  // 필수!
                position.getSymbol(),
                position.getEntryTime(),
                position.getCloseTime()
            );
            
            // 3. 진입/청산 Order 구분
            List<Order> entryOrders = orders.stream()
                .filter(o -> o.getPositionEffect() == PositionEffect.OPEN)
                .sorted(Comparator.comparing(Order::getOrderTime))
                .collect(Collectors.toList());
            
            List<Order> exitOrders = orders.stream()
                .filter(o -> o.getPositionEffect() == PositionEffect.CLOSE)
                .sorted(Comparator.comparing(Order::getOrderTime))
                .collect(Collectors.toList());
            
            // 4. Position 연결
            entryOrders.forEach(o -> o.setPosition(position));
            exitOrders.forEach(o -> o.setPosition(position));
            
            orderRepository.saveAll(orders);
            
            // 5. 성공 상태 업데이트
            position.setStatus(PositionStatus.CLOSED_MAPPED);
            positionRepository.save(position);
            
        } catch (Exception e) {
            // 실패 시 UNMAPPED 상태로 저장
            position.setStatus(PositionStatus.CLOSED_UNMAPPED);
            positionRepository.save(position);
            log.error("Order mapping failed for position: {}", position.getId(), e);
        }
    }
}
```

### OrderRepository
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // 중복 체크: 기존 Order ID 목록 조회
    @Query("""
        SELECT o.exchangeOrderId 
        FROM Order o 
        WHERE o.exchangeName = :exchangeName 
          AND o.exchangeOrderId IN :orderIds
        """)
    Set<String> findExistingOrderIds(
        @Param("exchangeName") ExchangeName exchangeName,
        @Param("orderIds") List<String> orderIds
    );
    
    // 매핑용: Position에 연결할 Order 조회
    @Query("""
        SELECT o FROM Order o 
        WHERE o.user.id = :userId 
          AND o.exchangeApiKey.id = :apiKeyId
          AND o.symbol = :symbol 
          AND o.orderTime BETWEEN :startTime AND :endTime
        ORDER BY o.orderTime ASC
        """)
    List<Order> findOrders(
        @Param("userId") Long userId,
        @Param("apiKeyId") Long apiKeyId,
        @Param("symbol") String symbol,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}
```

## 7. 이벤트 기반 처리

### PositionCloseEvent
```java
@Getter
@Builder
public class PositionCloseEvent {
    private Long positionId;
    private Long exchangeApiKeyId;
    private String symbol;
    private LocalDateTime closeTime;
}
```

### 이벤트 리스너
```java
@Component
public class OrderSyncEventListener {
    
    private final OrderMappingService orderMappingService;
    private final PositionRepository positionRepository;
    
    @EventListener
    @Async
    public void onPositionClose(PositionCloseEvent event) {
        Position position = positionRepository.findById(event.getPositionId())
            .orElseThrow();
        
        // 비동기로 매핑 수행
        orderMappingService.mapOrdersToPosition(position);
    }
}
```

## 8. Order 수집 전략 (배치)
```java
@Component
public class OrderSyncScheduler {
    
    @Scheduled(fixedDelay = 300000) // 5분마다
    public void syncOrders() {
        // 1. 모든 활성 API Key 조회
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findAllActive();
        
        for (ExchangeApiKey apiKey : apiKeys) {
            try {
                // 2. 거래소별 서비스 선택
                ExchangeOrderService service = getServiceForExchange(apiKey.getExchangeName());
                
                // 3. 최근 1시간 Order 조회
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneHourAgo = now.minusHours(1);
                
                List<Order> newOrders = service.fetchAndConvertOrders(
                    apiKey, 
                    null,  // 모든 심볼
                    oneHourAgo, 
                    now
                );
                
                // 4. 중복 체크
                List<String> fetchedOrderIds = newOrders.stream()
                    .map(Order::getExchangeOrderId)
                    .collect(Collectors.toList());
                
                Set<String> existingOrderIds = orderRepository.findExistingOrderIds(
                    apiKey.getExchangeName(),
                    fetchedOrderIds
                );
                
                // 5. 신규만 저장
                List<Order> ordersToSave = newOrders.stream()
                    .filter(o -> !existingOrderIds.contains(o.getExchangeOrderId()))
                    .collect(Collectors.toList());
                
                if (!ordersToSave.isEmpty()) {
                    orderRepository.saveAll(ordersToSave);
                    log.info("Synced {} new orders for API Key: {}", 
                             ordersToSave.size(), apiKey.getId());
                }
                
            } catch (Exception e) {
                log.error("Order sync failed for API Key: {}", apiKey.getId(), e);
            }
        }
    }
    
    private ExchangeOrderService getServiceForExchange(ExchangeName exchangeName) {
        // 팩토리 패턴으로 거래소별 서비스 반환
    }
}
```

## 9. 에러 처리 및 재시도

### 매핑 실패 재시도
```java
@Component
public class UnmappedPositionRetryScheduler {
    
    @Scheduled(cron = "0 */10 * * * *") // 10분마다
    public void retryUnmappedPositions() {
        // CLOSED_UNMAPPED 상태의 Position 조회
        List<Position> unmappedPositions = positionRepository
            .findByStatus(PositionStatus.CLOSED_UNMAPPED);
        
        for (Position position : unmappedPositions) {
            try {
                orderMappingService.mapOrdersToPosition(position);
            } catch (Exception e) {
                log.warn("Retry failed for position: {}", position.getId());
            }
        }
    }
}
```

### API 호출 실패 재시도
```java
@Service
public class BybitApiClient {
    
    @Retryable(
        value = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public BybitOrderResponse getOrderHistory(...) {
        // API 호출
    }
}
```

## 10. 구현 우선순위

### Phase 1: 기본 Order 수집
1. BybitOrderService 구현
    - API 호출 및 응답 파싱
    - Order 엔티티 변환
    - 필터링 로직
2. OrderRepository 구현
    - 중복 체크 쿼리
3. 배치 스케줄러 구현
    - 주기적 Order 동기화

### Phase 2: Position Close 매핑
1. OrderMappingService 구현
    - Order 조회 및 매핑 로직
2. PositionCloseEvent 및 리스너
3. Position 상태 관리 (PositionStatus enum)

### Phase 3: WebSocket 연동
1. BybitWebSocketService 구현
    - Position 변경 감지
    - PositionCloseEvent 발행
2. 실시간 Position 업데이트

### Phase 4: 안정화
1. 에러 처리 강화
2. 재시도 로직
3. 모니터링 및 로깅

## 11. 주의사항

### 필수 확인 사항
- ✅ Order 조회 시 반드시 exchangeApiKey.id로 필터링
- ✅ Position Close 시점에만 매핑 수행
- ✅ 중복 저장 방지 (exchangeOrderId 기준)
- ✅ 비동기 처리로 메인 플로우 블로킹 방지
- ✅ 실패 시 재시도 메커니즘

### 데이터 정합성
- Order는 Position 없이도 독립적으로 존재 가능
- Position은 매핑 전에도 OPEN 상태로 존재
- 매핑 실패해도 데이터는 보존됨
- CLOSED_UNMAPPED 상태로 재시도 대기

### 성능 고려사항
- 매핑은 비동기로 처리 (@Async)
- 배치는 API Rate Limit 고려하여 간격 조정 (5분)
- 인덱스: (exchange_api_key_id, symbol, order_time)

### REST API 배치 선택 이유
- **안정성**: 누락 없는 완벽한 데이터 수집
- **단순성**: 연결 관리, 재연결 로직 불필요
- **검증 가능**: 주기적 재검증 쉬움
- **실익**: Order는 분석용 데이터로 5분 지연 문제 없음
- **유지보수**: 구현 및 디버깅 용이
