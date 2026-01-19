package hello.tradexserver.controller;

import hello.tradexserver.openApi.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
