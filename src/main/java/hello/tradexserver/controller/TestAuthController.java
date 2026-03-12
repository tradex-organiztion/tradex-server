package hello.tradexserver.controller;

import hello.tradexserver.dto.response.AuthResponse;
import hello.tradexserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TestAuth", description = "[개발 전용] 테스트 로그인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Profile("!prod")
public class TestAuthController {

    private final AuthService authService;

    @Operation(summary = "[개발 전용] userId로 바로 토큰 발급", description = "QA용. prod 환경에서는 비활성화됩니다.")
    @PostMapping("/test-login")
    public ResponseEntity<AuthResponse> testLogin(@RequestParam Long userId) {
        AuthResponse response = authService.testLogin(userId);
        return ResponseEntity.ok(response);
    }
}
