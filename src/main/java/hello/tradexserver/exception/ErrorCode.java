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
    INVALID_VERIFICATION_TYPE(HttpStatus.BAD_REQUEST, "AUTH012", "유효하지 않은 인증 유형입니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}