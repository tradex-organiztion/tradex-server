package hello.tradexserver.service;

import hello.tradexserver.domain.ChatMessage;
import hello.tradexserver.domain.User;
import hello.tradexserver.exception.AuthException;
import hello.tradexserver.repository.ChatMessageRepository;
import hello.tradexserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.stereotype.Service;
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

import static hello.tradexserver.exception.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final StreamingChatModel streamingChatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    private static final int HISTORY_LIMIT = 5;

    public SseEmitter streamChat(Long userId, String question, List<MultipartFile> files) {
        SseEmitter emitter = new SseEmitter(300000L);

        // Security Context 캡처 (현재 요청 스레드에서)
        SecurityContext securityContext = SecurityContextHolder.getContext();

        new Thread(() -> {
            try {
                // 새 스레드에 Security Context 설정
                SecurityContextHolder.setContext(securityContext);

                // 사용자 조회
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new AuthException(USER_NOT_FOUND));

                // 채팅 히스토리 조회
                List<ChatMessage> history = chatMessageRepository
                        .findRecentHistory(userId, HISTORY_LIMIT);

                // 프롬프트 구성
                Prompt prompt = buildPrompt(question, files, history);
                log.info("Sending prompt to username: {}", user.getUsername());

                // 스트리밍 호출
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
                                    // Reactor 스레드에 Security Context 설정
                                    SecurityContextHolder.setContext(securityContext);
                                    try {
                                        // DB 저장
                                        saveChatMessage(user, question, response.toString());
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

    private Prompt buildPrompt(String question, List<MultipartFile> files, List<ChatMessage> history) {
        StringBuilder textContent = new StringBuilder();

        // 이전 대화 맥락
        if (!history.isEmpty()) {
            textContent.append("이전 대화:\n");
            history.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .forEach(msg -> {
                        textContent.append("Q: ").append(msg.getQuestion()).append("\n");
                        textContent.append("A: ").append(msg.getResponse()).append("\n\n");
                    });
        }

        // 현재 질문
        textContent.append("사용자 질문: ").append(question);

        // 파일 처리
        List<Media> mediaList = new ArrayList<>();
        StringBuilder fileContents = new StringBuilder();

        if (files != null && !files.isEmpty()) {
            int fileIndex = 1;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                String fileContent = processFile(file, mediaList);
                if (fileContent != null && !fileContent.isEmpty()) {
                    fileContents.append("\n\n--- 파일 ").append(fileIndex).append(": ")
                            .append(file.getOriginalFilename()).append(" ---\n")
                            .append(fileContent);
                    log.info(fileContents.toString());
                    fileIndex++;
                }
            }

            if (!fileContents.isEmpty()) {
                textContent.append("\n\n분석할 파일 내용:").append(fileContents);
            }
        }

        log.info("Prompt text length: {} chars, Images: {}", textContent.length(), mediaList.size());
//        log.info("Prompt text content: {}", textContent);

        // UserMessage 생성 (이미지가 있으면 Media와 함께)
        UserMessage userMessage;
        if (!mediaList.isEmpty()) {
            userMessage = new UserMessage(textContent.toString(), mediaList);
        } else {
            userMessage = new UserMessage(textContent.toString());
        }

        return new Prompt(userMessage);
    }

    private String processFile(MultipartFile file, List<Media> mediaList) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            return null;
        }

        try {
            // 이미지 파일
            if (contentType != null && contentType.startsWith("image/")) {
                MimeType mimeType = MimeType.valueOf(contentType);
                Media imageMedia = new Media(mimeType, file.getResource());
                mediaList.add(imageMedia);
                log.info("Image file attached: {}", fileName);
                return null;
            }

            // PDF 파일
            if (fileName.toLowerCase().endsWith(".pdf") ||
                    "application/pdf".equals(contentType)) {
                return extractPdfText(file);
            }

            // 엑셀 파일 (.xlsx)
            if (fileName.toLowerCase().endsWith(".xlsx") ||
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType)) {
                return extractExcelText(file, true);
            }

            // 엑셀 파일 (.xls)
            if (fileName.toLowerCase().endsWith(".xls") ||
                    "application/vnd.ms-excel".equals(contentType)) {
                return extractExcelText(file, false);
            }

            // CSV 파일
            if (fileName.toLowerCase().endsWith(".csv") ||
                    "text/csv".equals(contentType)) {
                return extractCsvText(file);
            }

            // 기타 텍스트 파일
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
        // GPT 토큰 제한을 고려하여 최대 50000자로 제한
        int maxLength = 50000;
        if (text.length() > maxLength) {
            log.warn("Text truncated from {} to {} chars", text.length(), maxLength);
            return text.substring(0, maxLength) + "\n\n[... 내용이 길어 일부 생략됨 ...]";
        }
        return text;
    }

    private void saveChatMessage(User user, String question, String response) {
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .question(question)
                .response(response)
                .build();

        chatMessageRepository.save(chatMessage);
    }
}
