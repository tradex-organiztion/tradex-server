package hello.tradexserver.controller;

import hello.tradexserver.dto.request.*;
import hello.tradexserver.dto.response.AuthResponse;
import hello.tradexserver.dto.response.FindEmailResponse;
import hello.tradexserver.dto.response.MessageResponse;
import hello.tradexserver.dto.response.UserResponse;
import hello.tradexserver.repository.UserRepository;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @Operation(summary = "SMS 인증번호 발송", description = """
            회원가입 또는 아이디 찾기를 위한 SMS 인증번호를 발송합니다.

            - type: SIGNUP(회원가입), FIND_EMAIL(아이디찾기), RESET_PASSWORD(비밀번호 재설정)
            - 아이디 찾기 시 미가입 번호는 보안을 위해 SMS 미발송 후 200 반환
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 처리 완료"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 전화번호 (회원가입 시)")
    })
    @PostMapping("/send-sms")
    public ResponseEntity<MessageResponse> sendSms(@Valid @RequestBody SendSmsRequest request) {
        authService.sendVerificationSms(request);
        return ResponseEntity.ok(MessageResponse.of("인증번호가 발송되었습니다."));
    }

    @Operation(summary = "SMS 인증번호 확인", description = "발송된 SMS 인증번호를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않거나 만료된 인증번호")
    })
    @PostMapping("/verify-sms")
    public ResponseEntity<MessageResponse> verifySms(@Valid @RequestBody VerifySmsRequest request) {
        authService.verifySms(request);
        return ResponseEntity.ok(MessageResponse.of("인증이 완료되었습니다."));
    }

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 사용자명, 전화번호로 회원가입합니다. 전화번호 인증이 선행되어야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 이메일 또는 전화번호")
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 Refresh Token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그아웃", description = "현재 사용자의 Refresh Token을 무효화합니다.")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "현재 사용자 정보 조회", description = "로그인한 사용자의 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return userRepository.findById(userDetails.getUserId())
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "프로필 완료", description = "거래소 API 키를 등록하여 프로필을 완료합니다.")
    @ApiResponse(responseCode = "200", description = "프로필 완료 성공")
    @PostMapping("/complete-profile")
    public ResponseEntity<UserResponse> completeProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CompleteProfileRequest request) {
        UserResponse response = authService.completeProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "아이디(이메일) 찾기", description = """
            전화번호 인증 후 등록된 이메일을 마스킹하여 반환합니다.

            - SMS 인증이 선행되어야 합니다.
            - 보안을 위해 미가입 번호도 200 반환 (maskedEmail: null)
            """)
    @ApiResponse(responseCode = "200", description = "요청 처리 완료 (maskedEmail이 null이면 미가입)")
    @PostMapping("/find-email")
    public ResponseEntity<FindEmailResponse> findEmail(@Valid @RequestBody FindEmailRequest request) {
        FindEmailResponse response = authService.findEmail(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "비밀번호 재설정 요청", description = """
            입력한 이메일로 비밀번호 재설정 링크를 발송합니다.

            - 보안을 위해 이메일 존재 여부와 관계없이 항상 200을 반환합니다.
            - 소셜 로그인 계정은 이메일이 발송되지 않습니다.

            **리다이렉션 URL 형식:** `https://tradex.so/reset-password?token={uuid}`
            """)
    @ApiResponse(responseCode = "200", description = "요청 처리 완료 (이메일 발송 여부는 알 수 없음)")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(MessageResponse.of("비밀번호 재설정 이메일이 발송되었습니다."));
    }

    @Operation(summary = "비밀번호 재설정", description = "이메일로 받은 토큰을 사용하여 새 비밀번호를 설정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않거나 만료된 토큰")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(MessageResponse.of("비밀번호가 성공적으로 변경되었습니다."));
    }

}