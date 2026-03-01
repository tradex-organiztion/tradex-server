package hello.tradexserver.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH001", "이미 존재하는 이메일입니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH002", "사용자를 찾을 수 없습니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH003", "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH004", "유효하지 않은 Refresh Token입니다"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH005", "만료된 Refresh Token입니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH006", "인증이 필요합니다"),

    // Phone Verification
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH007", "이미 등록된 전화번호입니다"),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH008", "유효하지 않거나 만료된 인증번호입니다"),
    PHONE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH009", "전화번호 인증이 필요합니다"),
    PHONE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH010", "등록된 전화번호가 없습니다"),

    // Password Reset
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "AUTH011", "유효하지 않거나 만료된 재설정 토큰입니다"),
    INVALID_VERIFICATION_TYPE(HttpStatus.BAD_REQUEST, "AUTH012", "유효하지 않은 인증 유형입니다"),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI001", "알림을 찾을 수 없습니다"),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTI002", "해당 알림에 접근 권한이 없습니다"),

    // Exchange API Key
    EXCHANGE_API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCH001", "API 키를 찾을 수 없습니다"),
    EXCHANGE_API_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXCH002", "해당 거래소의 API 키가 이미 존재합니다"),
    BITGET_PASSPHRASE_REQUIRED(HttpStatus.BAD_REQUEST, "EXCH003", "Bitget API 키 등록 시 passphrase는 필수입니다"),
    INVALID_API_KEY(HttpStatus.BAD_REQUEST, "EXCH004", "유효하지 않은 API 키입니다"),

    // Position
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "POS001", "포지션을 찾을 수 없습니다"),
    POSITION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POS002", "해당 포지션에 접근 권한이 없습니다"),
    EXCHANGE_POSITION_IMMUTABLE(HttpStatus.FORBIDDEN, "POS003", "거래소 포지션은 수정하거나 삭제할 수 없습니다"),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORD001", "오더를 찾을 수 없습니다"),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORD002", "해당 오더에 접근 권한이 없습니다"),
    EXCHANGE_ORDER_IMMUTABLE(HttpStatus.FORBIDDEN, "ORD003", "거래소 오더는 수정하거나 삭제할 수 없습니다"),

    // TradingJournal
    JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "JRN001", "매매일지를 찾을 수 없습니다"),
    JOURNAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "JRN002", "해당 매매일지에 접근 권한이 없습니다"),

    // TradingPrinciple
    TRADING_PRINCIPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "PRIN001", "매매 원칙을 찾을 수 없습니다"),

    // Chart Layout
    CHART_LAYOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHART001", "차트 레이아웃을 찾을 수 없습니다"),
    CHART_LAYOUT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHART002", "해당 차트 레이아웃에 접근 권한이 없습니다"),

    // Chat
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT001", "채팅 세션을 찾을 수 없습니다"),

    // Subscription
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUB001", "구독 정보를 찾을 수 없습니다"),
    BILLING_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "SUB002", "결제 수단 등록이 필요합니다"),
    ALREADY_SAME_PLAN(HttpStatus.BAD_REQUEST, "SUB003", "이미 동일한 플랜을 구독 중입니다"),
    SUBSCRIPTION_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "SUB004", "활성 구독이 없습니다"),
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SUB005", "결제에 실패했습니다"),
    FREE_PLAN_NO_BILLING(HttpStatus.BAD_REQUEST, "SUB006", "무료 플랜은 결제 수단이 필요하지 않습니다"),

    // S3 / File
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE001", "파일이 비어있습니다"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "FILE002", "이미지 파일만 업로드 가능합니다"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE003", "파일 크기는 10MB를 초과할 수 없습니다"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE004", "파일 업로드에 실패했습니다"),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE005", "파일 삭제에 실패했습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}