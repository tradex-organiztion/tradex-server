package hello.tradexserver.dto.chat;

public record JournalStatsResponse(
        int totalCount,
        int winCount,
        int lossCount,
        String winRate,
        String totalPnl,
        String avgPnl,
        String maxWin,
        String maxLoss
) {}
