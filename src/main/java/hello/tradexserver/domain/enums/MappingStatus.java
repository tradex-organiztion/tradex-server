package hello.tradexserver.domain.enums;

public enum MappingStatus {
    NONE,         // 오픈 포지션 (매핑 불필요)
    IN_PROGRESS,  // 매핑 진행 중
    MAPPED,       // 매핑 완료
    FAILED        // 매핑 실패 (재시도 대기)
}