package hello.tradexserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.tradexserver.domain.enums.ExchangeName;
import hello.tradexserver.dto.response.chart.BarData;
import hello.tradexserver.dto.response.chart.BarsResponse;
import hello.tradexserver.dto.response.chart.SymbolInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BINANCE_FAPI = "https://fapi.binance.com";
    private static final String BYBIT_API    = "https://api.bybit.com";
    private static final String BITGET_API   = "https://api.bitget.com";

    private static final List<String> SUPPORTED_RESOLUTIONS =
            List.of("1", "5", "15", "30", "60", "240", "1D", "1W", "1M");

    // 닫힌 캔들(과거 데이터): 5분 캐싱
    private static final long BARS_CACHE_TTL_SECONDS = 300L;
    // 현재 캔들(열린 봉): 10초 캐싱
    private static final long CURRENT_BAR_CACHE_TTL_SECONDS = 10L;

    // Binance exchangeInfo 인메모리 캐시 (심볼 검색/정보용)
    private volatile JsonNode exchangeInfoCache;
    private volatile long exchangeInfoCacheTime = 0;
    private static final long EXCHANGE_INFO_TTL_MS = 10 * 60 * 1000L;

    // ==================== BARS ====================

    public BarsResponse getBars(String symbol, String resolution, long from, long to,
                                Integer countBack, ExchangeName exchange) {
        String normalizedSymbol = symbol.replace("/", "").toUpperCase();
        String cacheKey = "chart:bars:" + exchange + ":" + normalizedSymbol + ":" + resolution + ":" + from + ":" + to;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof BarsResponse barsResponse) {
            log.debug("[Chart] 캐시 HIT: {}", cacheKey);
            return barsResponse;
        }

        try {
            BarsResponse response = switch (exchange) {
                case BINANCE -> fetchBinanceBars(normalizedSymbol, resolution, from, to, countBack);
                case BYBIT   -> fetchBybitBars(normalizedSymbol, resolution, from, to, countBack);
                case BITGET  -> fetchBitgetBars(normalizedSymbol, resolution, from, to, countBack);
            };

            if (!response.isNoData()) {
                // to가 현재 시각 기준 2분 이내면 열린 봉으로 판단 → 짧은 TTL
                long ttl = (System.currentTimeMillis() / 1000L - to) < 120
                        ? CURRENT_BAR_CACHE_TTL_SECONDS
                        : BARS_CACHE_TTL_SECONDS;
                redisTemplate.opsForValue().set(cacheKey, response, ttl, TimeUnit.SECONDS);
            }

            return response;
        } catch (Exception e) {
            log.error("[Chart] 캔들 조회 실패: exchange={}, symbol={}, resolution={}", exchange, symbol, resolution, e);
            return new BarsResponse(List.of(), true);
        }
    }

    private BarsResponse fetchBinanceBars(String symbol, String resolution,
                                          long from, long to, Integer countBack) throws Exception {
        String interval = toBinanceInterval(resolution);
        int limit = countBack != null ? Math.min(countBack, 1500) : 500;

        String url = BINANCE_FAPI + "/fapi/v1/klines"
                + "?symbol=" + symbol
                + "&interval=" + interval
                + "&startTime=" + (from * 1000L)
                + "&endTime=" + (to * 1000L)
                + "&limit=" + limit;

        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        List<List<Object>> raw = objectMapper.readValue(resp.getBody(), new TypeReference<>() {});
        if (raw.isEmpty()) return new BarsResponse(List.of(), true);

        // Binance 응답: [openTime, open, high, low, close, volume, ...]
        List<BarData> bars = raw.stream()
                .map(item -> new BarData(
                        ((Number) item.get(0)).longValue() / 1000L,
                        parseDouble(item.get(1)),
                        parseDouble(item.get(2)),
                        parseDouble(item.get(3)),
                        parseDouble(item.get(4)),
                        parseDouble(item.get(5))
                ))
                .toList();

        return new BarsResponse(bars, false);
    }

    private BarsResponse fetchBybitBars(String symbol, String resolution,
                                        long from, long to, Integer countBack) throws Exception {
        String interval = toBybitInterval(resolution);
        int limit = countBack != null ? Math.min(countBack, 1000) : 200;

        String url = BYBIT_API + "/v5/market/kline"
                + "?category=linear"
                + "&symbol=" + symbol
                + "&interval=" + interval
                + "&start=" + (from * 1000L)
                + "&end=" + (to * 1000L)
                + "&limit=" + limit;

        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode list = root.path("result").path("list");

        if (!list.isArray() || list.isEmpty()) return new BarsResponse(List.of(), true);

        // Bybit 응답: [startTime, open, high, low, close, volume, turnover] — 최신 순
        List<BarData> bars = new ArrayList<>();
        for (JsonNode item : list) {
            bars.add(new BarData(
                    Long.parseLong(item.get(0).asText()) / 1000L,
                    item.get(1).asDouble(),
                    item.get(2).asDouble(),
                    item.get(3).asDouble(),
                    item.get(4).asDouble(),
                    item.get(5).asDouble()
            ));
        }
        Collections.reverse(bars);

        return new BarsResponse(bars, false);
    }

    private BarsResponse fetchBitgetBars(String symbol, String resolution,
                                         long from, long to, Integer countBack) throws Exception {
        String granularity = toBitgetGranularity(resolution);
        int limit = countBack != null ? Math.min(countBack, 1000) : 200;

        String url = BITGET_API + "/api/v2/mix/market/candles"
                + "?symbol=" + symbol
                + "&productType=usdt-futures"
                + "&granularity=" + granularity
                + "&startTime=" + (from * 1000L)
                + "&endTime=" + (to * 1000L)
                + "&limit=" + limit;

        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode data = root.path("data");

        if (!data.isArray() || data.isEmpty()) return new BarsResponse(List.of(), true);

        // Bitget 응답: [timestamp, open, high, low, close, baseVol, quoteVol] — 최신 순
        List<BarData> bars = new ArrayList<>();
        for (JsonNode item : data) {
            bars.add(new BarData(
                    Long.parseLong(item.get(0).asText()) / 1000L,
                    Double.parseDouble(item.get(1).asText()),
                    Double.parseDouble(item.get(2).asText()),
                    Double.parseDouble(item.get(3).asText()),
                    Double.parseDouble(item.get(4).asText()),
                    Double.parseDouble(item.get(5).asText())
            ));
        }
        Collections.reverse(bars);

        return new BarsResponse(bars, false);
    }

    // ==================== SYMBOL INFO ====================

    public SymbolInfoResponse getSymbolInfo(String symbol, ExchangeName exchange) {
        String normalizedSymbol = symbol.replace("/", "").toUpperCase();
        try {
            if (exchange == ExchangeName.BINANCE) {
                return buildSymbolInfoFromBinance(normalizedSymbol, exchange.name());
            }
            return buildDefaultSymbolInfo(symbol, exchange.name());
        } catch (Exception e) {
            log.error("[Chart] 심볼 정보 조회 실패: symbol={}, exchange={}", symbol, exchange, e);
            return buildDefaultSymbolInfo(symbol, exchange.name());
        }
    }

    // ==================== SEARCH ====================

    public List<SymbolInfoResponse> searchSymbols(String query, ExchangeName exchange) {
        try {
            if (exchange == ExchangeName.BINANCE) {
                return searchBinanceSymbols(query, exchange.name());
            }
            return List.of();
        } catch (Exception e) {
            log.error("[Chart] 심볼 검색 실패: query={}, exchange={}", query, exchange, e);
            return List.of();
        }
    }

    // ==================== BINANCE SYMBOL HELPERS ====================

    private SymbolInfoResponse buildSymbolInfoFromBinance(String symbol, String exchangeName) throws Exception {
        JsonNode exchangeInfo = getBinanceExchangeInfo();
        for (JsonNode s : exchangeInfo.path("symbols")) {
            if (symbol.equals(s.path("symbol").asText())) {
                return toSymbolInfoResponse(s, exchangeName);
            }
        }
        return buildDefaultSymbolInfo(symbol, exchangeName);
    }

    private List<SymbolInfoResponse> searchBinanceSymbols(String query, String exchangeName) throws Exception {
        JsonNode exchangeInfo = getBinanceExchangeInfo();
        String upperQuery = query.toUpperCase();

        List<SymbolInfoResponse> results = new ArrayList<>();
        for (JsonNode s : exchangeInfo.path("symbols")) {
            if (!"TRADING".equals(s.path("status").asText())) continue;
            String sym = s.path("symbol").asText();
            if (sym.contains(upperQuery)) {
                results.add(toSymbolInfoResponse(s, exchangeName));
                if (results.size() >= 10) break;
            }
        }
        return results;
    }

    private SymbolInfoResponse toSymbolInfoResponse(JsonNode s, String exchangeName) {
        int pricePrecision = s.path("pricePrecision").asInt(2);
        String baseAsset = s.path("baseAsset").asText();
        String quoteAsset = s.path("quoteAsset").asText();
        String displaySymbol = baseAsset + "/" + quoteAsset;

        return SymbolInfoResponse.builder()
                .name(displaySymbol)
                .ticker(displaySymbol)
                .description(baseAsset + " / " + quoteAsset)
                .type("crypto")
                .session("24x7")
                .exchange(exchangeName)
                .timezone("Etc/UTC")
                .pricescale((int) Math.pow(10, pricePrecision))
                .minmov(1)
                .hasIntraday(true)
                .hasWeeklyAndMonthly(true)
                .supportedResolutions(SUPPORTED_RESOLUTIONS)
                .volumePrecision(8)
                .build();
    }

    private SymbolInfoResponse buildDefaultSymbolInfo(String symbol, String exchangeName) {
        return SymbolInfoResponse.builder()
                .name(symbol)
                .ticker(symbol)
                .description(symbol)
                .type("crypto")
                .session("24x7")
                .exchange(exchangeName)
                .timezone("Etc/UTC")
                .pricescale(100)
                .minmov(1)
                .hasIntraday(true)
                .hasWeeklyAndMonthly(true)
                .supportedResolutions(SUPPORTED_RESOLUTIONS)
                .volumePrecision(8)
                .build();
    }

    // Binance exchangeInfo 인메모리 캐시 (10분 TTL)
    private JsonNode getBinanceExchangeInfo() throws Exception {
        long now = System.currentTimeMillis();
        if (exchangeInfoCache != null && (now - exchangeInfoCacheTime) < EXCHANGE_INFO_TTL_MS) {
            return exchangeInfoCache;
        }
        ResponseEntity<String> resp = restTemplate.getForEntity(
                BINANCE_FAPI + "/fapi/v1/exchangeInfo", String.class);
        exchangeInfoCache = objectMapper.readTree(resp.getBody());
        exchangeInfoCacheTime = now;
        log.info("[Chart] Binance exchangeInfo 갱신 완료");
        return exchangeInfoCache;
    }

    // ==================== UTILS ====================

    private double parseDouble(Object obj) {
        return Double.parseDouble(obj.toString());
    }

    private String toBinanceInterval(String resolution) {
        return switch (resolution) {
            case "1"   -> "1m";
            case "3"   -> "3m";
            case "5"   -> "5m";
            case "15"  -> "15m";
            case "30"  -> "30m";
            case "60"  -> "1h";
            case "120" -> "2h";
            case "240" -> "4h";
            case "360" -> "6h";
            case "720" -> "12h";
            case "1D"  -> "1d";
            case "1W"  -> "1w";
            case "1M"  -> "1M";
            default    -> { log.warn("[Chart] 알 수 없는 resolution: {}", resolution); yield "1m"; }
        };
    }

    private String toBybitInterval(String resolution) {
        return switch (resolution) {
            case "1"   -> "1";
            case "3"   -> "3";
            case "5"   -> "5";
            case "15"  -> "15";
            case "30"  -> "30";
            case "60"  -> "60";
            case "120" -> "120";
            case "240" -> "240";
            case "360" -> "360";
            case "720" -> "720";
            case "1D"  -> "D";
            case "1W"  -> "W";
            case "1M"  -> "M";
            default    -> { log.warn("[Chart] 알 수 없는 resolution: {}", resolution); yield "1"; }
        };
    }

    private String toBitgetGranularity(String resolution) {
        return switch (resolution) {
            case "1"   -> "1min";
            case "3"   -> "3min";
            case "5"   -> "5min";
            case "15"  -> "15min";
            case "30"  -> "30min";
            case "60"  -> "1h";
            case "120" -> "2h";
            case "240" -> "4h";
            case "360" -> "6h";
            case "720" -> "12h";
            case "1D"  -> "1day";
            case "1W"  -> "1week";
            case "1M"  -> "1M";
            default    -> { log.warn("[Chart] 알 수 없는 resolution: {}", resolution); yield "1min"; }
        };
    }
}
