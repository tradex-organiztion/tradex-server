package hello.tradexserver.domain.enums;

public enum PositionStatus {
    OPEN,            // WebSocket으로 감지, 매핑 전
    CLOSING,         // Close 감지, 매핑 진행 중
    CLOSED_MAPPED,   // 매핑 완료
    CLOSED_UNMAPPED, // 매핑 실패 (재시도 대기)
    CLOSED           // 기존 호환용
}