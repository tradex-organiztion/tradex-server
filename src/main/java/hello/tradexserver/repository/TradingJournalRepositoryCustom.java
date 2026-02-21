package hello.tradexserver.repository;

import hello.tradexserver.dto.request.JournalStatsFilterRequest;

public interface TradingJournalRepositoryCustom {

    Object[] getJournalStats(Long userId, JournalStatsFilterRequest filter);
}
