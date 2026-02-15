package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.ChatHistoryResponse;
import hello.tradexserver.dto.response.ChatSessionResponse;
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

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(chatService.createSession(userDetails.getUserId()));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> getSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(chatService.getSessions(userDetails.getUserId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId
    ) {
        chatService.deleteSession(userDetails.getUserId(), sessionId);
        return ApiResponse.success("세션이 삭제되었습니다");
    }

    @GetMapping("/sessions/{sessionId}/history")
    public ApiResponse<ChatHistoryResponse> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(chatService.getHistory(userDetails.getUserId(), sessionId));
    }

    @PostMapping("/message")
    public SseEmitter postMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long sessionId,
            @RequestParam String question,
            @RequestPart(required = false) List<MultipartFile> files,
            HttpServletResponse response
    ) {
        Long userId = userDetails.getUserId();

        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        return chatService.streamChat(userId, sessionId, question, files);
    }
}