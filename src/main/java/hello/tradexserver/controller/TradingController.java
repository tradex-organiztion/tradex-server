package hello.tradexserver.controller;

import hello.tradexserver.dto.response.ApiResponse;
import hello.tradexserver.dto.response.PositionResponse;
import hello.tradexserver.security.CustomUserDetails;
import hello.tradexserver.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

//    @GetMapping("/positions")
//    public ApiResponse<Page<PositionResponse>> getPositions(
//            @AuthenticationPrincipal CustomUserDetails userDetails,
//            @RequestParam(name = "page",defaultValue = "0") int page,
//            @RequestParam(name = "size",defaultValue = "10") int size
//    ) {
//        return ApiResponse<List.of()>;
//    }
}
