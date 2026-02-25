package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.MarketCondition;
import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import java.time.LocalDate;
import java.util.List;
import hello.tradexserver.dto.request.JournalRequest;
import hello.tradexserver.dto.request.JournalStatsFilterRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.JournalDetailResponse;
import hello.tradexserver.dto.response.JournalStatsOptionsResponse;
import hello.tradexserver.dto.response.JournalStatsResponse;
import hello.tradexserver.dto.response.JournalSummaryResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.TradingJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
@Tag(name = "TradingJournal", description = "매매일지 조회/수정/삭제")
public class TradingJournalController {

    private final TradingJournalService tradingJournalService;

    @GetMapping("/stats/options")
    @Operation(summary = "통계 필터 선택지 조회", description = "사용자가 입력한 indicators, timeframes, technicalAnalyses 고유값 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<JournalStatsOptionsResponse>> getStatsOptions(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                tradingJournalService.getStatsOptions(userDetails.getUserId())));
    }

    @GetMapping("/stats")
    @Operation(summary = "매매일지 통계 조회", description = """
            선택한 필터 조건(AND)에 해당하는 CLOSED 포지션들의 통합 통계를 반환합니다.
            아무 필터도 선택하지 않으면 전체 CLOSED 포지션 대상입니다.

            **tradingStyle:** SCALPING (1일 미만 보유) | SWING (1일 이상 보유)
            """)
    public ResponseEntity<ApiResponse<JournalStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "지표 (복수 선택 가능)") @RequestParam(required = false) List<String> indicators,
            @Parameter(description = "타임프레임 (복수 선택 가능)") @RequestParam(required = false) List<String> timeframes,
            @Parameter(description = "기술적 분석 (복수 선택 가능)") @RequestParam(required = false) List<String> technicalAnalyses,
            @Parameter(description = "트레이딩 스타일 (SCALPING | SWING)") @RequestParam(required = false) String tradingStyle,
            @Parameter(description = "포지션 방향 (LONG | SHORT)") @RequestParam(required = false) PositionSide positionSide,
            @Parameter(description = "시장 상황 (UPTREND | DOWNTREND | SIDEWAYS)") @RequestParam(required = false) MarketCondition marketCondition
    ) {
        JournalStatsFilterRequest filter = new JournalStatsFilterRequest(
                indicators, timeframes, technicalAnalyses, tradingStyle, positionSide, marketCondition);
        return ResponseEntity.ok(ApiResponse.success(
                tradingJournalService.getStats(userDetails.getUserId(), filter)));
    }

    @GetMapping
    @Operation(summary = "매매일지 목록 조회", description = """
            포지션 요약 정보를 포함한 매매일지 목록을 페이지네이션으로 조회합니다.

            **필터링 옵션:** symbol, side (LONG/SHORT), positionStatus (OPEN/CLOSED), startDate/endDate (yyyy-MM-dd)
            """)
    public ResponseEntity<ApiResponse<Page<JournalSummaryResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거래 페어 필터", example = "BTCUSDT")
            @RequestParam(required = false) String symbol,
            @Parameter(description = "포지션 방향 (LONG/SHORT)")
            @RequestParam(required = false) PositionSide side,
            @Parameter(description = "포지션 상태 (OPEN/CLOSED)")
            @RequestParam(required = false) PositionStatus positionStatus,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)", example = "2025-01-01")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)", example = "2025-12-31")
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<JournalSummaryResponse> response = tradingJournalService.getList(
                userDetails.getUserId(), symbol, side, positionStatus, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{journalId}")
    @Operation(summary = "매매일지 상세 조회", description = "포지션, 오더 목록, 저널 내용을 한 번에 조회합니다.")
    public ResponseEntity<ApiResponse<JournalDetailResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long journalId
    ) {
        JournalDetailResponse response = tradingJournalService.getDetail(
                userDetails.getUserId(), journalId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{journalId}")
    @Operation(summary = "매매일지 수정", description = "매매일지 내용(계획, 시나리오, 리뷰)을 수정합니다.")
    public ResponseEntity<ApiResponse<JournalDetailResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long journalId,
            @RequestBody JournalRequest request
    ) {
        log.info("[JournalController] 매매일지 수정 요청 - userId: {}, journalId: {}",
                userDetails.getUserId(), journalId);
        JournalDetailResponse response = tradingJournalService.update(
                userDetails.getUserId(), journalId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping(value = "/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "차트 스크린샷 업로드", description = "차트 이미지를 업로드하고 URL을 반환합니다.")
    public ResponseEntity<ApiResponse<String>> uploadScreenshot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        String url = tradingJournalService.uploadScreenshot(userDetails.getUserId(), file);
        return ResponseEntity.ok(ApiResponse.success("업로드 완료", url));
    }

    @DeleteMapping("/{journalId}")
    @Operation(summary = "매매일지 삭제", description = "매매일지와 연결된 포지션, 오더를 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long journalId
    ) {
        log.info("[JournalController] 매매일지 삭제 요청 - userId: {}, journalId: {}",
                userDetails.getUserId(), journalId);
        tradingJournalService.delete(userDetails.getUserId(), journalId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}