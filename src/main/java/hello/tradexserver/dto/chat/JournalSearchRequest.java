package hello.tradexserver.dto.chat;

/**
 * AI Function Call - 매매일지 검색 파라미터
 * 모든 필드는 optional이며, null이면 해당 조건을 적용하지 않음.
 * 결과가 너무 적어지지 않도록 핵심 조건 1~2개만 사용 권장.
 */
public record JournalSearchRequest(
        /** 거래 심볼. 예: "BTCUSDT", "ETHUSDT". null이면 전체 심볼 */
        String symbol,

        /** 포지션 방향. "LONG" 또는 "SHORT". null이면 전체 */
        String side,

        /** 거래소명. 예: "BINANCE", "BYBIT". null이면 전체 거래소 */
        String exchangeName,

        /** 검색 시작 날짜. 형식: "yyyy-MM-dd". null이면 제한 없음 */
        String startDate,

        /** 검색 종료 날짜. 형식: "yyyy-MM-dd". null이면 제한 없음 */
        String endDate,

        /** 최소 실현 손익 (USDT). 예: -100.0. null이면 제한 없음 */
        Double minPnl,

        /** 최대 실현 손익 (USDT). 예: 500.0. null이면 제한 없음 */
        Double maxPnl,

        /**
         * 손익 필터. true=익절(realizedPnl > 0)만, false=손절(realizedPnl <= 0)만.
         * null이면 전체. minPnl/maxPnl보다 우선 적용됨.
         */
        Boolean winOnly,

        /** true이면 감정적 매매로 분류된 일지만 조회. null이면 전체 */
        Boolean isEmotionalTrade,

        /** true이면 비계획 진입으로 분류된 일지만 조회. null이면 전체 */
        Boolean isUnplannedEntry,

        /** true이면 복기(reviewContent)가 작성된 일지만 조회. null이면 전체 */
        Boolean hasReview,

        /** 정렬 기준. "exitTime"(기본), "pnl", "entryTime" */
        String sortBy,

        /** 반환할 최대 건수. 기본 20, 최대 50. 내용 기반 판단이 필요하면 크게 설정 */
        Integer limit
) {}
