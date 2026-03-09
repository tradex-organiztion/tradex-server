package hello.tradexserver.dto.chat;

/**
 * AI Function Call - 매매 통계 집계 파라미터
 * 개별 일지 조회 없이 집계 결과(건수, 승률, 손익 합계 등)가 필요할 때 사용.
 * 모든 필드 optional.
 */
public record JournalStatsRequest(
        /** 거래 심볼. 예: "BTCUSDT". null이면 전체 */
        String symbol,

        /** 포지션 방향. "LONG" 또는 "SHORT". null이면 전체 */
        String side,

        /** 거래소명. 예: "BINANCE". null이면 전체 */
        String exchangeName,

        /** 집계 시작 날짜. 형식: "yyyy-MM-dd" */
        String startDate,

        /** 집계 종료 날짜. 형식: "yyyy-MM-dd" */
        String endDate
) {}
