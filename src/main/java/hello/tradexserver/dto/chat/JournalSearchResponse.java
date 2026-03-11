package hello.tradexserver.dto.chat;

import java.util.List;

public record JournalSearchResponse(
        int totalCount,
        List<JournalSummary> journals
) {

    public record JournalSummary(
            String symbol,
            String side,
            String exchangeName,
            Integer leverage,
            String realizedPnl,
            String entryTime,
            String exitTime,
            List<String> indicators,
            List<String> timeframes,
            String entryReason,
            String reviewContent,
            String refinedText,
            Boolean isEmotionalTrade,
            Boolean isUnplannedEntry
    ) {}
}
