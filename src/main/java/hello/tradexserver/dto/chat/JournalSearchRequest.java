package hello.tradexserver.dto.chat;

public record JournalSearchRequest(
        String symbol,
        String startDate
) {}
