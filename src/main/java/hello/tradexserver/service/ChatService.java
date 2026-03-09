package hello.tradexserver.service;

import hello.tradexserver.domain.ChatMessage;
import hello.tradexserver.domain.ChatSession;
import hello.tradexserver.domain.User;
import hello.tradexserver.dto.chat.JournalSearchRequest;
import hello.tradexserver.dto.chat.JournalStatsRequest;
import hello.tradexserver.dto.response.ChatHistoryResponse;
import hello.tradexserver.dto.response.ChatSessionResponse;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.ChatSessionRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static hello.tradexserver.exception.ErrorCode.SESSION_NOT_FOUND;
import static hello.tradexserver.exception.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final StreamingChatModel streamingChatModel;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatContextService chatContextService;

    private static final int MEMORY_LIMIT = 5;
    private static final int TITLE_MAX_LENGTH = 30;

    @Transactional
    public ChatSessionResponse createSession(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(USER_NOT_FOUND));

        ChatSession session = ChatSession.builder()
                .user(user)
                .build();

        return new ChatSessionResponse(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ChatSessionResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AuthException(SESSION_NOT_FOUND));

        return new ChatHistoryResponse(
                session.getId(),
                session.getTitle(),
                chatMessageRepository.findAllByChatSessionIdOrderByCreatedAtAsc(sessionId)
        );
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AuthException(SESSION_NOT_FOUND));

        chatMessageRepository.deleteAllByChatSessionId(sessionId);
        chatSessionRepository.delete(session);
    }

    public SseEmitter streamChat(Long userId, Long sessionId, String question, List<MultipartFile> files) {
        SseEmitter emitter = new SseEmitter(300000L);

        // Security Context 캡처 (현재 요청 스레드에서)
        SecurityContext securityContext = SecurityContextHolder.getContext();

        new Thread(() -> {
            try {
                SecurityContextHolder.setContext(securityContext);

                // 세션 소유권 검증
                ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                        .orElseThrow(() -> new AuthException(SESSION_NOT_FOUND));

                // 메모리: 최근 MEMORY_LIMIT개 대화 조회
                List<Message> memoryMessages = getMemoryMessages(sessionId);

                // 현재 질문 구성 (파일 포함)
                UserMessage currentMessage = buildUserMessage(question, files);

                // SystemMessage 주입 (개인화된 트레이딩 컨텍스트)
                SystemMessage systemMessage = chatContextService.buildSystemMessage(userId);

                // FunctionCallback 등록
                FunctionCallbackWrapper<JournalSearchRequest, ?> journalCallback =
                        FunctionCallbackWrapper.builder(
                                        (JournalSearchRequest req) -> chatContextService.searchJournals(userId, req))
                                .withName("searchTradingJournals")
                                .withDescription("""
                                        트레이더의 매매일지 상세 목록을 검색합니다.
                                        진입 이유, 복기 내용, 차트 패턴 등 개별 매매 내용을 분석할 때 사용하세요.

                                        [파라미터 가이드]
                                        - 결과가 너무 적어지지 않도록 핵심 조건 1~2개만 사용하세요.
                                        - 내용 기반으로 AI가 직접 판단해야 할 경우 limit을 크게(30~50) 설정하세요.
                                        - 집계/통계(승률, 합계 등)가 필요하면 getJournalStats를 대신 사용하세요.

                                        [파라미터]
                                        - symbol: 거래 심볼 (예: "BTCUSDT"). null이면 전체.
                                        - side: "LONG" 또는 "SHORT". null이면 전체.
                                        - exchangeName: 거래소 (예: "BINANCE", "BYBIT"). null이면 전체.
                                        - startDate/endDate: 기간 필터 (yyyy-MM-dd). null이면 제한 없음.
                                        - minPnl/maxPnl: 손익 범위 (USDT). null이면 제한 없음.
                                        - winOnly: true=익절만, false=손절만, null=전체.
                                        - isEmotionalTrade: true이면 감정적 매매만.
                                        - isUnplannedEntry: true이면 비계획 진입만.
                                        - hasReview: true이면 복기가 작성된 일지만.
                                        - sortBy: "exitTime"(기본), "pnl", "entryTime".
                                        - limit: 반환 건수 (기본 20, 최대 50).
                                        """)
                                .withInputType(JournalSearchRequest.class)
                                .build();

                FunctionCallbackWrapper<JournalStatsRequest, ?> statsCallback =
                        FunctionCallbackWrapper.builder(
                                        (JournalStatsRequest req) -> chatContextService.getJournalStats(userId, req))
                                .withName("getJournalStats")
                                .withDescription("""
                                        조건별 매매 통계를 집계합니다.
                                        "BTCUSDT 승률이 어때?", "이번 달 손익 합계는?" 같은 집계 질문에 사용하세요.
                                        개별 일지 내용 없이 건수, 승률, 손익 합계/평균/최대/최소를 반환합니다.

                                        [파라미터]
                                        - symbol: 거래 심볼. null이면 전체.
                                        - side: "LONG" 또는 "SHORT". null이면 전체.
                                        - exchangeName: 거래소명. null이면 전체.
                                        - startDate/endDate: 집계 기간 (yyyy-MM-dd).
                                        """)
                                .withInputType(JournalStatsRequest.class)
                                .build();

                OpenAiChatOptions options = new OpenAiChatOptions.Builder()
                        .withFunctionCallbacks(List.of(journalCallback, statsCallback))
                        .build();

                // 최종 프롬프트: [시스템 메시지, 메모리 메시지들... , 현재 질문]
                List<Message> allMessages = new ArrayList<>();
                allMessages.add(systemMessage);
                allMessages.addAll(memoryMessages);
                allMessages.add(currentMessage);
                Prompt prompt = new Prompt(allMessages, options);
                log.info("Sending prompt to userId: {}, sessionId: {}, memory size: {}", userId, sessionId, memoryMessages.size());
                allMessages.forEach(msg -> log.info("[{}] {}", msg.getMessageType(), msg.getContent()));

                StringBuilder response = new StringBuilder();
                streamingChatModel.stream(prompt)
                        .subscribe(
                                chunk -> {
                                    try {
                                        String content = chunk.getResult().getOutput().getContent();
                                        if (content != null) {
                                            response.append(content);
                                            emitter.send(SseEmitter.event().data(content));
                                        }
                                    } catch (IOException e) {
                                        log.error("SSE send error", e);
                                    }
                                },
                                error -> {
                                    log.error("Chat stream error", error);
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    SecurityContextHolder.setContext(securityContext);
                                    try {
                                        // 메모리 저장
                                        saveMessage(session, question, response.toString());

                                        // 첫 메시지인 경우 세션 title 자동 설정
                                        if (session.getTitle() == null) {
                                            String title = question.length() > TITLE_MAX_LENGTH
                                                    ? question.substring(0, TITLE_MAX_LENGTH)
                                                    : question;
                                            session.updateTitle(title);
                                            chatSessionRepository.save(session);
                                        }
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                    emitter.complete();
                                }
                        );

            } catch (Exception e) {
                log.error("Chat Error", e);
                emitter.completeWithError(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }).start();

        return emitter;
    }

    private List<Message> getMemoryMessages(Long sessionId) {
        List<ChatMessage> history = chatMessageRepository.findRecentBySessionId(sessionId, MEMORY_LIMIT);
        List<Message> messages = new ArrayList<>();
        // DESC로 조회됐으므로 역순으로 정렬 (오래된 것 먼저)
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            messages.add(new UserMessage(msg.getQuestion()));
            messages.add(new AssistantMessage(msg.getResponse()));
        }
        return messages;
    }

    private void saveMessage(ChatSession session, String question, String response) {
        chatMessageRepository.save(ChatMessage.builder()
                .user(session.getUser())
                .chatSession(session)
                .question(question)
                .response(response)
                .build());
    }

    private UserMessage buildUserMessage(String question, List<MultipartFile> files) {
        List<Media> mediaList = new ArrayList<>();
        StringBuilder textContent = new StringBuilder(question);

        if (files != null && !files.isEmpty()) {
            StringBuilder fileContents = new StringBuilder();
            int fileIndex = 1;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String fileContent = processFile(file, mediaList);
                if (fileContent != null && !fileContent.isEmpty()) {
                    fileContents.append("\n\n--- 파일 ").append(fileIndex).append(": ")
                            .append(file.getOriginalFilename()).append(" ---\n")
                            .append(fileContent);
                    fileIndex++;
                }
            }
            if (!fileContents.isEmpty()) {
                textContent.append("\n\n분석할 파일 내용:").append(fileContents);
            }
        }

        log.info("UserMessage text length: {} chars, images: {}", textContent.length(), mediaList.size());

        if (!mediaList.isEmpty()) {
            return new UserMessage(textContent.toString(), mediaList);
        }
        return new UserMessage(textContent.toString());
    }

    private String processFile(MultipartFile file, List<Media> mediaList) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            return null;
        }

        try {
            if (contentType != null && contentType.startsWith("image/")) {
                MimeType mimeType = MimeType.valueOf(contentType);
                Media imageMedia = new Media(mimeType, file.getResource());
                mediaList.add(imageMedia);
                log.info("Image file attached: {}", fileName);
                return null;
            }

            if (fileName.toLowerCase().endsWith(".pdf") ||
                    "application/pdf".equals(contentType)) {
                return extractPdfText(file);
            }

            if (fileName.toLowerCase().endsWith(".xlsx") ||
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType)) {
                return extractExcelText(file, true);
            }

            if (fileName.toLowerCase().endsWith(".xls") ||
                    "application/vnd.ms-excel".equals(contentType)) {
                return extractExcelText(file, false);
            }

            if (fileName.toLowerCase().endsWith(".csv") ||
                    "text/csv".equals(contentType)) {
                return extractCsvText(file);
            }

            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            log.info("Text file attached: {}", fileName);
            return text;

        } catch (Exception e) {
            log.error("File processing error: {}", fileName, e);
            return null;
        }
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("PDF extracted: {} ({} chars)", file.getOriginalFilename(), text.length());
            return truncateIfNeeded(text);
        }
    }

    private String extractExcelText(MultipartFile file, boolean isXlsx) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (Workbook workbook = isXlsx ?
                new XSSFWorkbook(file.getInputStream()) :
                new HSSFWorkbook(file.getInputStream())) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(getCellValue(cell));
                    }
                    sb.append(String.join("\t", cells)).append("\n");
                }
                sb.append("\n");
            }
        }

        log.info("Excel extracted: {} ({} chars)", file.getOriginalFilename(), sb.length());
        return truncateIfNeeded(sb.toString());
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String extractCsvText(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        log.info("CSV extracted: {} ({} chars)", file.getOriginalFilename(), sb.length());
        return truncateIfNeeded(sb.toString());
    }

    private String truncateIfNeeded(String text) {
        int maxLength = 50000;
        if (text.length() > maxLength) {
            log.warn("Text truncated from {} to {} chars", text.length(), maxLength);
            return text.substring(0, maxLength) + "\n\n[... 내용이 길어 일부 생략됨 ...]";
        }
        return text;
    }
}