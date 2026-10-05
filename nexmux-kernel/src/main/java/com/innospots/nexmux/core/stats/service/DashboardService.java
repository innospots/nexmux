package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.DashboardVo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DashboardService {

    private static final String DIM_MODEL = "model_code";
    private static final String DIM_CLIENT = "client_id";
    private static final int REALTIME_WINDOW_SECONDS = 60;

    private final StatsQueryDao statsQueryDao;

    public DashboardVo dashboard(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> summary = statsQueryDao.summarize(start, end);
        long requests = StatsMapping.longOf(summary, "requests");
        Map<String, Object> realtime = statsQueryDao.realtime(REALTIME_WINDOW_SECONDS);
        return new DashboardVo(
                requests,
                StatsMapping.longOf(summary, "tokens"),
                StatsMapping.decimalOf(summary, "costUsd"),
                StatsMapping.decimalOf(summary, "billedUsd"),
                StatsMapping.rate(StatsMapping.longOf(summary, "successCount"), requests),
                StatsMapping.longOf(summary, "errorCount"),
                StatsMapping.doubleOf(summary, "avgLatencyMs"),
                StatsMapping.longOf(summary, "activeClients"),
                statsQueryDao.trendByDay(start, end).stream()
                        .map(row -> new DashboardVo.TrendPoint(
                                StatsMapping.stringOf(row, "day"),
                                StatsMapping.longOf(row, "requests"),
                                StatsMapping.longOf(row, "tokens"),
                                StatsMapping.decimalOf(row, "costUsd"),
                                StatsMapping.doubleOf(row, "avgLatencyMs")))
                        .toList(),
                toDistribution(statsQueryDao.requestsByDimension(start, end, DIM_MODEL)),
                toDistribution(statsQueryDao.requestsByDimension(start, end, DIM_CLIENT)),
                StatsMapping.longOf(realtime, "requests"),
                StatsMapping.longOf(realtime, "tokens"));
    }

    private List<DashboardVo.DimensionCount> toDistribution(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new DashboardVo.DimensionCount(
                        StatsMapping.stringOf(row, "dim"),
                        StatsMapping.longOf(row, "requests")))
                .toList();
    }
}
