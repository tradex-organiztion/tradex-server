package hello.tradexserver.controller;

import hello.tradexserver.dto.request.ChatMessageRequest;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/message")
    public SseEmitter postMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String question,
            @RequestPart(required = false) List<MultipartFile> files,
            HttpServletResponse response
            ) {
        // 인증 확인을 먼저 수행 (헤더 설정 전에)
        Long userId = userDetails.getUserId();

        // 인증 확인 후 헤더 설정 / 모아서 한꺼번에 오거나 캐시된 이전 응답이 오지 않도록, 한 글자씩 스트리밍 되도록
        response.setHeader("Cache-Control", "no-cache"); // 브라우저/프록시가 응답을 캐시하지 않도록 함
        response.setHeader("X-Accel-Buffering", "no"); // Nginx 리버스 프록시의 버퍼링 비활성화

        return chatService.streamChat(userId, question, files);
    }
}
