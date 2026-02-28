package hello.tradexserver.controller;

import hello.tradexserver.dto.request.ResetPasswordByPhoneRequest;
import hello.tradexserver.dto.response.MessageResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 설정 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "휴대폰 번호로 비밀번호 변경", description = """
            SMS 인증 완료 후 새 비밀번호를 설정합니다.

            **사전 조건:** `POST /api/auth/send-sms` (type: RESET_PASSWORD) → `POST /api/auth/verify-sms` (type: RESET_PASSWORD) 완료 후 호출
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "SMS 인증이 완료되지 않았거나 만료됨"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "등록된 사용자 없음")
    })
    @PostMapping("/me/password")
    public ResponseEntity<MessageResponse> resetPasswordByPhone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ResetPasswordByPhoneRequest request) {
        authService.resetPasswordByPhone(userDetails.getUserId(), request);
        return ResponseEntity.ok(MessageResponse.of("비밀번호가 성공적으로 변경되었습니다."));
    }
}