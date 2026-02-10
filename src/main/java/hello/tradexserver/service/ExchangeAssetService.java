package hello.tradexserver.service;

import hello.tradexserver.domain.ExchangeApiKey;
import hello.tradexserver.openApi.rest.ExchangeFactory;
import hello.tradexserver.openApi.rest.ExchangeRestClient;
import hello.tradexserver.openApi.rest.dto.WalletBalanceResponse;
import hello.tradexserver.repository.ExchangeApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import hello.tradexserver.openApi.rest.dto.CoinBalanceDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeAssetService {

    private final ExchangeApiKeyRepository exchangeApiKeyRepository;
    private final ExchangeFactory exchangeFactory;

    /**
     * 사용자 전체 자산 합계 조회
     */
    public BigDecimal getTotalAsset(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);

        return apiKeys.stream()
                .map(key -> {
                    try {
                        ExchangeRestClient client = exchangeFactory.getExchangeService(
                                key.getExchangeName(), key.getApiKey(), key.getApiSecret()
                        );
                        BigDecimal asset = client.getAsset();
                        return asset != null ? asset : BigDecimal.ZERO;
                    } catch (Exception e) {
                        log.warn("Failed to get asset from {}: {}", key.getExchangeName(), e.getMessage());
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 모든 거래소의 지갑 잔고를 통합 조회
     * - totalEquity 합산, coins 목록 통합
     */
    public WalletBalanceResponse getAggregatedWalletBalance(Long userId) {
        List<ExchangeApiKey> apiKeys = exchangeApiKeyRepository.findActiveByUserId(userId);

        BigDecimal totalEquity = BigDecimal.ZERO;
        List<CoinBalanceDto> allCoins = new ArrayList<>();

        for (ExchangeApiKey key : apiKeys) {
            try {
                ExchangeRestClient client = exchangeFactory.getExchangeService(
                        key.getExchangeName(), key.getApiKey(), key.getApiSecret()
                );
                WalletBalanceResponse walletBalance = client.getWalletBalance();

                if (walletBalance != null) {
                    if (walletBalance.getTotalEquity() != null) {
                        totalEquity = totalEquity.add(walletBalance.getTotalEquity());
                    }
                    if (walletBalance.getCoins() != null) {
                        allCoins.addAll(walletBalance.getCoins());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get wallet balance from {}: {}", key.getExchangeName(), e.getMessage());
            }
        }

        return WalletBalanceResponse.builder()
                .totalEquity(totalEquity)
                .coins(allCoins)
                .build();
    }
}
