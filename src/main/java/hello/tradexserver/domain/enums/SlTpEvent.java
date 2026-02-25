package hello.tradexserver.domain.enums;

public enum SlTpEvent {
    /** 최초 설정 */
    SET,
    /** 가격 변경 */
    CHANGED,
    /** 수동 취소 (Bybit는 TRIGGERED 구분 불가) */
    CANCELED,
    /** SL/TP 가격에 도달해 체결 (Binance / Bitget) */
    TRIGGERED
}
