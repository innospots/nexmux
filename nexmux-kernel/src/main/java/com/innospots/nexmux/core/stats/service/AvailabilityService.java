package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.AvailabilityVo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AvailabilityService {

    private static final String DIM_PROVIDER = "provider_code";
    private static final String DIM_MODEL_PROVIDER = "model_code || '@' || provider_code";
    private static final int RECENT_ERROR_LIMIT = 10;

    private final StatsQueryDao statsQueryDao;

    public AvailabilityVo availability(LocalDateTime start, LocalDateTime end) {
        return new AvailabilityVo(
                StatsMapping.toUsageRows(statsQueryDao.usageByDimension(start, end, DIM_PROVIDER))
                        .stream().map(AvailabilityService::toQualityRow).toList(),
                StatsMapping.toUsageRows(statsQueryDao.usageByDimension(start, end, DIM_MODEL_PROVIDER))
                        .stream().map(AvailabilityService::toQualityRow).toList(),
                statsQueryDao.recentErrors(RECENT_ERROR_LIMIT).stream()
                        .map(AvailabilityService::toRecentError)
                        .toList());
    }

    private static AvailabilityVo.QualityRow toQualityRow(com.innospots.nexmux.core.stats.domain.vo.UsageRowVo row) {
        return new AvailabilityVo.QualityRow(
                row.key(),
                row.requests(),
                row.errorCount(),
                row.successRate(),
                row.avgLatencyMs(),
                row.avgUpstreamMs(),
                row.failoverRate(),
                row.avgAttempts(),
                row.billedUsd(),
                row.costUsd());
    }

    private static AvailabilityVo.RecentError toRecentError(Map<String, Object> row) {
        return new AvailabilityVo.RecentError(
                StatsMapping.stringOf(row, "requestId"),
                StatsMapping.stringOf(row, "requestedAt"),
                StatsMapping.stringOf(row, "modelCode"),
                StatsMapping.stringOf(row, "providerCode"),
                StatsMapping.stringOf(row, "errorCode"));
    }
}
