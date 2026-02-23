# 리스크 분석 API 쿼리 최적화

## 엔티티 관계

```
Position (N개)
  ├── TradingJournal  @OneToOne(mappedBy, LAZY)
  └── List<Order>     @OneToMany(mappedBy, LAZY)
```

---

## Before — 순진한 구현 (N+1 문제)

### 문제가 되는 코드

```java
// ❌ 서비스 레이어에서 lazy loading을 그냥 호출
List<Position> positions = positionRepository.findByUserIdAndStatus(userId, CLOSED);

for (Position p : positions) {
    TradingJournal journal = p.getTradingJournal(); // → SELECT * FROM trading_journals WHERE position_id = ?
    List<Order> orders = p.getOrders();             // → SELECT * FROM orders WHERE position_id = ?
    // 분석 로직...
}
```

### 실제로 발생하는 쿼리 수

```
[Query 1]  SELECT * FROM positions WHERE user_id = ? AND status = 'CLOSED'
           → N개 포지션 반환

[Query 2]  SELECT * FROM trading_journals WHERE position_id = 1   ← p[0].getTradingJournal()
[Query 3]  SELECT * FROM orders          WHERE position_id = 1   ← p[0].getOrders()
[Query 4]  SELECT * FROM trading_journals WHERE position_id = 2   ← p[1].getTradingJournal()
[Query 5]  SELECT * FROM orders          WHERE position_id = 2   ← p[1].getOrders()
...
[Query 2N] SELECT * FROM trading_journals WHERE position_id = N
[Query 2N+1] SELECT * FROM orders        WHERE position_id = N
```

| 포지션 수 | 발생 쿼리 수 |
|-----------|-------------|
| 30건 | 61개 |
| 100건 | 201개 |
| 300건 | 601개 |

### Fetch Join을 한 번에 다 걸면?

```java
// ❌ 이것도 안 됨
@Query("""
    SELECT p FROM Position p
    LEFT JOIN FETCH p.tradingJournal
    LEFT JOIN FETCH p.orders
    WHERE ...
    """)
```

`p.orders`는 `OneToMany`이므로 JOIN 시 rows가 Order 수만큼 **카테시안 곱**으로 폭발.
컬렉션이 2개 이상이면 Hibernate가 `MultipleBagFetchException`을 던짐.

---

## After — 2-Query 전략

### 핵심 아이디어

| 쿼리 | 대상 | 이유 |
|------|------|------|
| Query 1 | `Position + TradingJournal` (JOIN FETCH) | OneToOne이라 카테시안 곱 없음 |
| Query 2 | `Order WHERE position_id IN (...)` | 전체를 한 번에 bulk 조회 |
| In-Memory | 이후 모든 분석 로직 | 추가 쿼리 없음 |

**결과: 데이터 양과 무관하게 항상 쿼리 2개.**

---

### Query 1 — Position + TradingJournal

```java
// PositionRepository.java
@Query("""
    SELECT p FROM Position p
    LEFT JOIN FETCH p.tradingJournal
    WHERE p.user.id = :userId
      AND p.status = 'CLOSED'
      AND (:exchangeName IS NULL OR p.exchangeName = :exchangeName)
      AND p.exitTime >= :startDate
      AND p.exitTime <= :endDate
    ORDER BY p.entryTime ASC
    """)
List<Position> findClosedWithJournalForRiskAnalysis(
    @Param("userId") Long userId,
    @Param("exchangeName") ExchangeName exchangeName,
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate
);
```

> **왜 `entryTime ASC` 정렬인가?**
> 감정적 재진입, 연속진입, 과신진입, 역포지션 등 "15분 윈도우 체크"는 모두
> 시간 순서대로 나열된 포지션 리스트를 슬라이딩하면서 판단하기 때문.

---

### Query 2 — Orders bulk 조회

```java
// OrderRepository.java
@Query("""
    SELECT o FROM Order o
    WHERE o.position.id IN :positionIds
      AND o.positionEffect = 'OPEN'
    ORDER BY o.fillTime ASC
    """)
List<Order> findOpenOrdersByPositionIds(@Param("positionIds") List<Long> positionIds);
```

> **왜 `positionEffect = OPEN`만?**
> 물타기 감지는 "추가 진입 시점의 미실현 PnL"이 기준이므로
> Close 오더는 처음부터 제외해 불필요한 데이터 로딩을 줄임.

---

### 서비스 레이어

```java
public RiskAnalysisResponse analyze(Long userId, ExchangeName exchangeName,
                                    LocalDateTime startDate, LocalDateTime endDate) {

    // ✅ Query 1: Position + TradingJournal
    List<Position> positions = positionRepository
        .findClosedWithJournalForRiskAnalysis(userId, exchangeName, startDate, endDate);

    if (positions.isEmpty()) {
        return RiskAnalysisResponse.empty();
    }

    // ✅ Query 2: Orders (물타기 계산용)
    List<Long> positionIds = positions.stream()
        .map(Position::getId)
        .toList();
    List<Order> openOrders = orderRepository
        .findOpenOrdersByPositionIds(positionIds);

    // ✅ In-Memory 그룹핑 — 이후 쿼리 없음
    Map<Long, List<Order>> ordersByPosition = openOrders.stream()
        .collect(Collectors.groupingBy(o -> o.getPosition().getId()));

    // 분석 실행
    return RiskAnalysisResponse.builder()
        .totalTrades(positions.size())
        .entryRisk(analyzeEntryRisk(positions))
        .exitRisk(analyzeExitRisk(positions))
        .positionManagementRisk(analyzePositionManagementRisk(positions, ordersByPosition))
        .timeRisk(analyzeTimeRisk(positions))
        .emotionalRisk(analyzeEmotionalRisk(positions))
        .build();
}
```

---

### 각 리스크 항목별 쿼리 의존성

| 리스크 항목 | 의존 데이터 | 처리 위치 |
|-------------|------------|-----------|
| 계획 외 진입 | Journal.entryScenario | Query 1 결과 |
| 감정적 재진입 | Position 리스트 (시간순) | In-Memory 슬라이딩 윈도우 |
| 연속진입 (15분 3회) | Position 리스트 (시간순) | In-Memory 슬라이딩 윈도우 |
| SL 미준수 | Journal.plannedStopLoss, Position.avgExitPrice | Query 1 결과 |
| 조기 익절 | Journal.plannedTargetPrice, Position.avgExitPrice | Query 1 결과 |
| 평균 손절 지연 | SL 미준수 케이스 오차 평균 | In-Memory |
| 평균 R/R 비율 | Position.realizedPnl | Query 1 결과 |
| **물타기 빈도** | **Position + Orders (OPEN)** | **Query 2 결과** |
| 시간대별 승률 | Position.entryTime, realizedPnl | Query 1 결과 |
| 시장 상황별 승률 | Position.marketCondition, realizedPnl | Query 1 결과 |
| 과신 진입 | Position 리스트 (시간순) | In-Memory 슬라이딩 윈도우 |
| 손절 후 역포지션 | Position 리스트 (시간순) | In-Memory 슬라이딩 윈도우 |

---

## 쿼리 수 비교

| 포지션 수 | Before (N+1) | After (2-Query) | 절감율 |
|-----------|-------------|-----------------|--------|
| 30건 | 61개 | **2개** | -97% |
| 100건 | 201개 | **2개** | -99% |
| 300건 | 601개 | **2개** | -99.7% |

---

## 주의사항 — IN절 규모

포지션이 대량일 경우 `IN (:positionIds)`의 파라미터 수가 많아질 수 있음.

```
PostgreSQL: IN절 파라미터 수 제한 없음 (단, 수천 건부터 플래너 성능 저하 가능)
실용적 기준: 1,000건 이하는 문제없음
```

트레이딩 앱 특성상 사용자 1명의 기간별 포지션이 수천 건을 초과하는 경우는 드물어 **현재는 대응 불필요**.
향후 필요 시 분할 처리:

```java
// 필요 시 500건 단위로 나눠서 조회 후 합산
List<List<Long>> batches = Lists.partition(positionIds, 500);
List<Order> orders = batches.stream()
    .flatMap(batch -> orderRepository.findOpenOrdersByPositionIds(batch).stream())
    .toList();
```