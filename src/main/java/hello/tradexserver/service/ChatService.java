package hello.tradexserver.service;

import hello.tradexserver.chat.DbChatMemory;
import hello.tradexserver.domain.ChatSession;
import hello.tradexserver.domain.User;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
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
    private final DbChatMemory chatMemory;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

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

        chatMemory.clear(sessionId.toString());
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
                List<Message> memoryMessages = chatMemory.get(sessionId.toString(), MEMORY_LIMIT);

                // 현재 질문 구성 (파일 포함)
                UserMessage currentMessage = buildUserMessage(question, files);

                // 최종 프롬프트: [메모리 메시지들... , 현재 질문]
                List<Message> allMessages = new ArrayList<>(memoryMessages);
                allMessages.add(currentMessage);
                Prompt prompt = new Prompt(allMessages);

                log.info("Sending prompt to userId: {}, sessionId: {}, memory size: {}", userId, sessionId, memoryMessages.size());

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
                                        chatMemory.add(sessionId.toString(), List.of(
                                                new UserMessage(question),
                                                new AssistantMessage(response.toString())
                                        ));

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