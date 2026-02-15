package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.PositionSide;
import hello.tradexserver.domain.enums.PositionStatus;
import java.time.LocalDate;
import hello.tradexserver.dto.request.JournalRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.JournalDetailResponse;
import hello.tradexserver.dto.response.JournalSummaryResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.TradingJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
@Tag(name = "TradingJournal", description = "매매일지 조회/수정/삭제")
public class TradingJournalController {

    private final TradingJournalService tradingJournalService;

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