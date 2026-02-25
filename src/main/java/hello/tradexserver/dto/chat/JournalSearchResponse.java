package hello.tradexserver.dto.chat;

import java.util.List;

public record JournalSearchResponse(List<JournalSummary> journals) {

    public record JournalSummary(
            String symbol,
            String side,
            String realizedPnl,
            String exitTime,
            String entryReason,
            String reviewContent,
            String refinedText
    ) {}
}