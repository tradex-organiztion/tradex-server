package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.domain.Position;
import hello.tradexserver.domain.TradingJournal;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.domain.enums.OrderSide;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import hello.tradexserver.event.PositionCloseEvent;
import hello.tradexserver.openApi.rest.dto.BinancePositionRisk;
import hello.tradexserver.openApi.rest.dto.BitgetPositionItem;
import hello.tradexserver.openApi.rest.dto.BybitPositionRestItem;
import hello.tradexserver.openApi.rest.position.BinancePositionRestService;
import hello.tradexserver.openApi.rest.position.BitgetPositionRestService;
import hello.tradexserver.openApi.rest.position.BybitPositionRestService;
import hello.tradexserver.openApi.webSocket.PositionListener;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import hello.tradexserver.repository.OrderRepository;
import hello.tradexserver.repository.PositionRepository;
import hello.tradexserver.repository.TradingJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionTrackingService implements PositionListener {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final TradingJournalRepository tradingJournalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BybitPositionRestService bybitPositionRestService;
    private final BinancePositionRestService binancePositionRestService;
    private final BitgetPositionRestService bitgetPositionRestService;

    @Override
    @Transactional
    public void onPositionUpdate(Position wsPosition) {
        ExchangeApiKey apiKey = wsPosition.getExchangeApiKey();
        if (apiKey == null) {
            log.warn("[PositionTracking] exchangeApiKey 없음 - symbol: {}", wsPosition.getSymbol());
            return;
        }

        // DB에서 fresh ExchangeApiKey 조회 (detached 엔티티 문제 방지)
        ExchangeApiKey freshApiKey = exchangeApiKeyRepository.findById(apiKey.getId())
                .orElse(null);
        if (freshApiKey == null) {
            log.warn("[PositionTracking] API Key 조회 실패 - apiKeyId: {}", apiKey.getId());
            return;
        }

        log.info("[PositionTracking] Position Update - symbol: {}, side: {}, size: {}, entryPrice: {}",
                wsPosition.getSymbol(), wsPosition.getSide(),
                wsPosition.getCurrentSize(), wsPosition.getAvgEntryPrice());

        Optional<Position> existingOpt = positionRepository.findOpenPositionByApiKey(
                freshApiKey.getId(), wsPosition.getSymbol(), wsPosition.getSide()
        );

        if (existingOpt.isPresent()) {
            // 기존 포지션 업데이트
            Position existing = existingOpt.get();
            existing.updateFromWebSocket(
                    wsPosition.getAvgEntryPrice(),
                    wsPosition.getCurrentSize(),
                    wsPosition.getLeverage(),
                    wsPosition.getRealizedPnl()
            );
            positionRepository.save(existing);
            log.info("[PositionTracking] 기존 Position 업데이트 - id: {}, symbol: {}", existing.getId(), existing.getSymbol());
        } else {
            // 신규 포지션 저장 - entryTime 결정 우선순위:
            // 1. WS에서 전달된 entryTime (Binance "T" 등)
            // 2. 가장 최근 진입 오더의 fillTime
            // 3. fallback: now()
            LocalDateTime entryTime;
            if (wsPosition.getEntryTime() != null) {
                entryTime = wsPosition.getEntryTime();
            } else {
                OrderSide orderSide = wsPosition.getSide() == PositionSide.LONG ? OrderSide.BUY : OrderSide.SELL;
                entryTime = orderRepository
                        .findLatestOpenOrderFillTime(freshApiKey.getId(), wsPosition.getSymbol(), orderSide)
                        .orElse(LocalDateTime.now());
            }

            Position newPosition = Position.builder()
                    .user(freshApiKey.getUser())
                    .exchangeApiKey(freshApiKey)
                    .exchangeName(freshApiKey.getExchangeName())
                    .symbol(wsPosition.getSymbol())
                    .side(wsPosition.getSide())
                    .avgEntryPrice(wsPosition.getAvgEntryPrice())
                    .currentSize(wsPosition.getCurrentSize())
                    .leverage(wsPosition.getLeverage())
                    .targetPrice(wsPosition.getTargetPrice())
                    .stopLossPrice(wsPosition.getStopLossPrice())
                    .realizedPnl(wsPosition.getRealizedPnl())
                    .entryTime(entryTime)
                    .status(PositionStatus.OPEN)
                    .build();
            positionRepository.save(newPosition);
            log.info("[PositionTracking] 신규 Position 저장 - symbol: {}, side: {}", newPosition.getSymbol(), newPosition.getSide());

            // 매매일지 자동 생성
            TradingJournal journal = TradingJournal.builder()
                    .position(newPosition)
                    .user(freshApiKey.getUser())
                    .build();
            tradingJournalRepository.save(journal);
            log.info("[PositionTracking] 매매일지 자동 생성 - positionId: {}", newPosition.getId());
        }
    }

    @Override
    @Transactional
    public void onPositionClosed(Position wsPosition) {
        ExchangeApiKey apiKey = wsPosition.getExchangeApiKey();
        if (apiKey == null) {
            log.warn("[PositionTracking] exchangeApiKey 없음 - symbol: {}", wsPosition.getSymbol());
            return;
        }

        ExchangeApiKey freshApiKey = exchangeApiKeyRepository.findById(apiKey.getId())
                .orElse(null);
        if (freshApiKey == null) {
            log.warn("[PositionTracking] API Key 조회 실패 - apiKeyId: {}", apiKey.getId());
            return;
        }

        log.info("[PositionTracking] Position Closed - symbol: {}, side: {}, pnl: {}",
                wsPosition.getSymbol(), wsPosition.getSide(), wsPosition.getRealizedPnl());

        Optional<Position> existingOpt;
        if (wsPosition.getSide() != null) {
            // 헷지 모드 또는 side가 명확한 경우
            existingOpt = positionRepository.findOpenPositionByApiKey(
                    freshApiKey.getId(), wsPosition.getSymbol(), wsPosition.getSide()
            );
        } else {
            // 단방향 모드: positionIdx=0, side="" → symbol로만 조회
            existingOpt = positionRepository.findOpenPositionByApiKeyAndSymbol(
                    freshApiKey.getId(), wsPosition.getSymbol()
            );
        }

        if (existingOpt.isPresent()) {
            Position existing = existingOpt.get();
            log.info("[PositionTracking] exchangeUpdateTime 값: {}", wsPosition.getExchangeUpdateTime());

            existing.closingPosition(wsPosition.getExchangeUpdateTime(), PositionStatus.CLOSING);
            positionRepository.save(existing);
            log.info("[PositionTracking] Position CLOSING - id: {}, symbol: {}", existing.getId(), existing.getSymbol());
            log.info("[PositionTracking] Position CLOSING - exitTime: {}", existing.getExitTime());

            // Order 매핑 비동기 처리 트리거
            eventPublisher.publishEvent(PositionCloseEvent.builder()
                    .positionId(existing.getId())
                    .build());
        } else {
            log.warn("[PositionTracking] Close할 OPEN Position 없음 - apiKeyId: {}, symbol: {}, side: {}",
                    freshApiKey.getId(), wsPosition.getSymbol(), wsPosition.getSide());
        }
    }

    /**
     * WebSocket 재연결 시 REST API로 포지션 상태 보완
     * 1. DB OPEN 포지션 중 REST에 없는 것 → 종료 처리
     * 2. REST에 있는 것 → 최신 상태로 업데이트
     */
    @Override
    @Async
    @Transactional
    public void onReconnected(ExchangeApiKey apiKey) {
        ExchangeApiKey freshApiKey = exchangeApiKeyRepository.findById(apiKey.getId()).orElse(null);
        if (freshApiKey == null) return;

        ExchangeName exchange = freshApiKey.getExchangeName();
        switch (exchange) {
            case BYBIT -> reconnectBybit(freshApiKey);
            case BINANCE -> reconnectBinance(freshApiKey);
            case BITGET -> reconnectBitget(freshApiKey);
        }
    }

    private void reconnectBybit(ExchangeApiKey freshApiKey) {
        log.info("[PositionTracking] Bybit 재연결 Gap 보완 시작 - apiKeyId: {}", freshApiKey.getId());

        List<Position> dbOpenPositions = positionRepository.findAllOpenByApiKeyId(freshApiKey.getId());
        if (dbOpenPositions.isEmpty()) {
            log.info("[PositionTracking] 보완할 OPEN 포지션 없음 - apiKeyId: {}", freshApiKey.getId());
            return;
        }

        List<BybitPositionRestItem> restPositions = bybitPositionRestService.getOpenPositions(freshApiKey);
        Map<String, BybitPositionRestItem> restMap = restPositions.stream()
                .collect(Collectors.toMap(
                        p -> p.getSymbol() + "_" + convertBybitSide(p.getSide()),
                        p -> p,
                        (a, b) -> a
                ));

        for (Position dbPos : dbOpenPositions) {
            String key = dbPos.getSymbol() + "_" + dbPos.getSide().name();
            BybitPositionRestItem restPos = restMap.get(key);

            if (restPos == null) {
                log.info("[PositionTracking] Gap 종료 감지 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
                dbPos.closingPosition(LocalDateTime.now(), PositionStatus.CLOSING);
                positionRepository.save(dbPos);
                eventPublisher.publishEvent(PositionCloseEvent.builder()
                        .positionId(dbPos.getId())
                        .build());
            } else {
                dbPos.updateFromWebSocket(
                        parseBigDecimal(restPos.getAvgPrice()),
                        parseBigDecimal(restPos.getSize()),
                        parseInteger(restPos.getLeverage()),
                        parseBigDecimal(restPos.getCurRealisedPnl())
                );
                positionRepository.save(dbPos);
                log.debug("[PositionTracking] Gap 업데이트 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
            }
        }

        log.info("[PositionTracking] Bybit 재연결 Gap 보완 완료 - apiKeyId: {}, 처리 {}건",
                freshApiKey.getId(), dbOpenPositions.size());
    }

    private void reconnectBinance(ExchangeApiKey freshApiKey) {
        log.info("[PositionTracking] Binance 재연결 Gap 보완 시작 - apiKeyId: {}", freshApiKey.getId());

        List<Position> dbOpenPositions = positionRepository.findAllOpenByApiKeyId(freshApiKey.getId());
        if (dbOpenPositions.isEmpty()) {
            log.info("[PositionTracking] 보완할 OPEN 포지션 없음 - apiKeyId: {}", freshApiKey.getId());
            return;
        }

        List<BinancePositionRisk> restPositions = binancePositionRestService.getOpenPositions(freshApiKey);
        Map<String, BinancePositionRisk> restMap = restPositions.stream()
                .collect(Collectors.toMap(
                        p -> p.getSymbol() + "_" + convertBinanceSide(p.getPositionSide(), p.getPositionAmt()),
                        p -> p,
                        (a, b) -> a
                ));

        for (Position dbPos : dbOpenPositions) {
            String key = dbPos.getSymbol() + "_" + dbPos.getSide().name();
            BinancePositionRisk restPos = restMap.get(key);

            if (restPos == null) {
                log.info("[PositionTracking] Gap 종료 감지 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
                dbPos.closingPosition(LocalDateTime.now(), PositionStatus.CLOSING);
                positionRepository.save(dbPos);
                eventPublisher.publishEvent(PositionCloseEvent.builder()
                        .positionId(dbPos.getId())
                        .build());
            } else {
                BigDecimal posAmt = parseBigDecimal(restPos.getPositionAmt());
                dbPos.updateFromWebSocket(
                        parseBigDecimal(restPos.getEntryPrice()),
                        posAmt.abs(),
                        parseInteger(restPos.getLeverage()),
                        parseBigDecimal(restPos.getUnrealizedProfit())
                );
                positionRepository.save(dbPos);
                log.debug("[PositionTracking] Gap 업데이트 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
            }
        }

        log.info("[PositionTracking] Binance 재연결 Gap 보완 완료 - apiKeyId: {}, 처리 {}건",
                freshApiKey.getId(), dbOpenPositions.size());
    }

    private void reconnectBitget(ExchangeApiKey freshApiKey) {
        log.info("[PositionTracking] Bitget 재연결 Gap 보완 시작 - apiKeyId: {}", freshApiKey.getId());

        List<Position> dbOpenPositions = positionRepository.findAllOpenByApiKeyId(freshApiKey.getId());
        if (dbOpenPositions.isEmpty()) {
            log.info("[PositionTracking] 보완할 OPEN 포지션 없음 - apiKeyId: {}", freshApiKey.getId());
            return;
        }

        List<BitgetPositionItem> restPositions = bitgetPositionRestService.getOpenPositions(freshApiKey);
        Map<String, BitgetPositionItem> restMap = restPositions.stream()
                .collect(Collectors.toMap(
                        p -> p.getInstId() + "_" + convertBitgetSide(p.getHoldSide(), p.getTotal()),
                        p -> p,
                        (a, b) -> a
                ));

        for (Position dbPos : dbOpenPositions) {
            String key = dbPos.getSymbol() + "_" + dbPos.getSide().name();
            BitgetPositionItem restPos = restMap.get(key);

            if (restPos == null) {
                log.info("[PositionTracking] Gap 종료 감지 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
                dbPos.closingPosition(LocalDateTime.now(), PositionStatus.CLOSING);
                positionRepository.save(dbPos);
                eventPublisher.publishEvent(PositionCloseEvent.builder()
                        .positionId(dbPos.getId())
                        .build());
            } else {
                dbPos.updateFromWebSocket(
                        parseBigDecimal(restPos.getOpenPriceAvg()),
                        parseBigDecimal(restPos.getTotal()).abs(),
                        parseInteger(restPos.getLeverage()),
                        parseBigDecimal(restPos.getAchievedProfits())
                );
                positionRepository.save(dbPos);
                log.debug("[PositionTracking] Gap 업데이트 - positionId: {}, symbol: {}", dbPos.getId(), dbPos.getSymbol());
            }
        }

        log.info("[PositionTracking] Bitget 재연결 Gap 보완 완료 - apiKeyId: {}, 처리 {}건",
                freshApiKey.getId(), dbOpenPositions.size());
    }

    private String convertBitgetSide(String holdSide, String total) {
        if ("long".equalsIgnoreCase(holdSide)) return PositionSide.LONG.name();
        if ("short".equalsIgnoreCase(holdSide)) return PositionSide.SHORT.name();
        // "net": amount 부호로 방향 결정
        BigDecimal amt = parseBigDecimal(total);
        return amt.compareTo(BigDecimal.ZERO) >= 0 ? PositionSide.LONG.name() : PositionSide.SHORT.name();
    }

    private String convertBybitSide(String bybitSide) {
        return "Buy".equalsIgnoreCase(bybitSide) ? PositionSide.LONG.name() : PositionSide.SHORT.name();
    }

    private String convertBinanceSide(String positionSide, String positionAmt) {
        if ("LONG".equals(positionSide)) return PositionSide.LONG.name();
        if ("SHORT".equals(positionSide)) return PositionSide.SHORT.name();
        // BOTH: amount 부호로 방향 결정
        BigDecimal amt = parseBigDecimal(positionAmt);
        return amt.compareTo(BigDecimal.ZERO) >= 0 ? PositionSide.LONG.name() : PositionSide.SHORT.name();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }
}