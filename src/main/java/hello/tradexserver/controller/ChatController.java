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
            @RequestPart MultipartFile file,
            HttpServletResponse response
            ) {
        // 인증 확인을 먼저 수행 (헤더 설정 전에)
        Long userId = userDetails.getUserId();

        // 인증 확인 후 헤더 설정
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        log.info(question);
        return chatService.streamChat(userId, question, file);
    }
}
