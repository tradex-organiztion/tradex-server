package hello.tradexserver.controller;

import hello.tradexserver.dto.request.OrderRequest;
import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.OrderResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Order", description = "오더 수동 추가/수정/삭제")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/positions/{positionId}/orders")
    @Operation(summary = "오더 수동 추가", description = "포지션에 오더를 수동으로 추가합니다. 청산 포지션이면 자동 재계산됩니다.")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long positionId,
            @Valid @RequestBody OrderRequest request
    ) {
        log.info("[OrderController] 오더 추가 요청 - userId: {}, positionId: {}",
                userDetails.getUserId(), positionId);
        OrderResponse response = orderService.create(userDetails.getUserId(), positionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/orders/{orderId}")
    @Operation(summary = "오더 수정", description = "오더 데이터를 수정합니다. 청산 포지션이면 자동 재계산됩니다.")
    public ResponseEntity<ApiResponse<OrderResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId,
            @RequestBody OrderRequest request
    ) {
        log.info("[OrderController] 오더 수정 요청 - userId: {}, orderId: {}",
                userDetails.getUserId(), orderId);
        OrderResponse response = orderService.update(userDetails.getUserId(), orderId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/api/orders/{orderId}")
    @Operation(summary = "오더 삭제", description = "오더를 삭제합니다. 청산 포지션이면 자동 재계산됩니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long orderId
    ) {
        log.info("[OrderController] 오더 삭제 요청 - userId: {}, orderId: {}",
                userDetails.getUserId(), orderId);
        orderService.delete(userDetails.getUserId(), orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}