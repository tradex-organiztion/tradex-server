package hello.tradexserver.domain.enums;

public enum NotificationType {
    POSITION_ENTRY("포지션 진입"),
    POSITION_EXIT("포지션 종료"),
    RISK_WARNING("리스크 경고");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }
}
