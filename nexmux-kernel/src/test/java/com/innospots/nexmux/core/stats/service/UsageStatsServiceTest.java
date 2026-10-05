package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.AvailabilityVo;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UsageStatsServiceTest {

    private StatsQueryDao statsQueryDao;
    private UsageStatsService usageService;
    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        statsQueryDao = Mockito.mock(StatsQueryDao.class);
        usageService = new UsageStatsService(statsQueryDao);
        availabilityService = new AvailabilityService(statsQueryDao);
    }

    private Map<String, Object> aggRow(String dim) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("dim", dim);
        row.put("requests", 100L);
        row.put("inputTokens", 210000L);
        row.put("outputTokens", 180000L);
        row.put("cacheReadTokens", 50000L);
        row.put("costUsd", new BigDecimal("1.06"));
        row.put("billedUsd", new BigDecimal("1.27"));
        row.put("successCount", 97L);
        row.put("errorCount", 3L);
        row.put("avgLatencyMs", 320.0);
        row.put("avgTtftMs", 180.0);
        row.put("avgUpstreamMs", 295.0);
        row.put("failoverCount", 2L);
        row.put("retriesTotal", 5L);
        return row;
    }

    @Test
    void usageByModelComputesRatesAndAttempts() {
        when(statsQueryDao.usageByDimension(any(), any(), any())).thenReturn(List.of(aggRow("openai/o4-mini")));

        List<UsageRowVo> rows = usageService.usageByModel(
                LocalDateTime.of(2026, 9, 12, 0, 0), LocalDateTime.of(2026, 9, 19, 0, 0));

        assertThat(rows).hasSize(1);
        UsageRowVo row = rows.get(0);
        assertThat(row.key()).isEqualTo("openai/o4-mini");
        assertThat(row.requests()).isEqualTo(100L);
        assertThat(row.successRate()).isEqualTo(0.97);
        assertThat(row.failoverRate()).isEqualTo(0.02);
        assertThat(row.avgAttempts()).isEqualTo(1.05);
        assertThat(row.billedUsd()).isEqualByComparingTo("1.27");
    }

    @Test
    void availabilityAssemblesQualityCompareAndRecentErrors() {
        when(statsQueryDao.usageByDimension(any(), any(), any())).thenReturn(List.of(aggRow("openai")));
        when(statsQueryDao.recentErrors(any(Integer.class))).thenReturn(List.of(
                Map.of("requestId", "req-3d8a9c07", "requestedAt", "2026-09-17T22:41:55",
                        "modelCode", "openai/o4-mini", "providerCode", "openai", "errorCode", "UPSTREAM_502")));

        AvailabilityVo vo = availabilityService.availability(
                LocalDateTime.of(2026, 9, 12, 0, 0), LocalDateTime.of(2026, 9, 19, 0, 0));

        assertThat(vo.providers()).hasSize(1);
        assertThat(vo.providers().get(0).key()).isEqualTo("openai");
        assertThat(vo.providers().get(0).errorCount()).isEqualTo(3L);
        assertThat(vo.compare()).hasSize(1);
        assertThat(vo.recentErrors()).hasSize(1);
        assertThat(vo.recentErrors().get(0).errorCode()).isEqualTo("UPSTREAM_502");
    }
}
