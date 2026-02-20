package hello.tradexserver.controller;

import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.dto.response.chart.BarsResponse;
import hello.tradexserver.dto.response.chart.SymbolInfoResponse;
import hello.tradexserver.service.ChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
@Tag(name = "Chart", description = "TradingView 차트 데이터 API")
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/bars")
    @Operation(
            summary = "캔들 데이터 조회",
            description = """
                    TradingView용 OHLCV 캔들 데이터를 반환합니다.
                    - from / to 는 Unix timestamp **초 단위**
                    - 프론트에서 ms 변환 필요 (× 1000)
                    - resolution: 1·5·15·30·60·240·1D·1W·1M
                    - noData=true 이면 해당 구간에 데이터 없음
                    """
    )
    public BarsResponse getBars(
            @Parameter(description = "심볼명", example = "BTC/USDT")
            @RequestParam String symbol,

            @Parameter(description = "타임프레임 (분 단위 또는 1D·1W·1M)", example = "60")
            @RequestParam String resolution,

            @Parameter(description = "시작 시간 (Unix timestamp, 초 단위)", example = "1708300800")
            @RequestParam long from,

            @Parameter(description = "종료 시간 (Unix timestamp, 초 단위)", example = "1708387200")
            @RequestParam long to,

            @Parameter(description = "요청할 봉 개수 (선택). 미입력 시 from~to 범위 내 최대 반환", example = "300")
            @RequestParam(required = false) Integer countBack,

            @Parameter(description = "거래소 (기본값: BINANCE)", example = "BINANCE")
            @RequestParam(required = false, defaultValue = "BINANCE") ExchangeName exchange) {
        return chartService.getBars(symbol, resolution, from, to, countBack, exchange);
    }

    @GetMapping("/symbols")
    @Operation(
            summary = "심볼 정보 조회",
            description = """
                    TradingView 심볼 메타데이터를 반환합니다.
                    - pricescale: 가격 소수점 자릿수 (100 = 소수 2자리)
                    - session: 거래 시간 (crypto는 항상 24x7)
                    - supported_resolutions: 지원 타임프레임 목록
                    """
    )
    public SymbolInfoResponse getSymbolInfo(
            @Parameter(description = "심볼명", example = "BTC/USDT")
            @RequestParam String symbol,

            @Parameter(description = "거래소 (기본값: BINANCE)", example = "BINANCE")
            @RequestParam(required = false, defaultValue = "BINANCE") ExchangeName exchange) {
        return chartService.getSymbolInfo(symbol, exchange);
    }

    @GetMapping("/search")
    @Operation(
            summary = "심볼 검색",
            description = """
                    키워드로 심볼을 검색합니다.
                    - 대소문자 구분 없음
                    - 최대 10개 반환
                    - 현재 BINANCE 거래소 기준 검색 지원
                    """
    )
    public List<SymbolInfoResponse> searchSymbols(
            @Parameter(description = "검색 키워드", example = "BTC")
            @RequestParam String query,

            @Parameter(description = "거래소 (기본값: BINANCE)", example = "BINANCE")
            @RequestParam(required = false, defaultValue = "BINANCE") ExchangeName exchange) {
        return chartService.searchSymbols(query, exchange);
    }
}
