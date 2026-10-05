package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.dao.StatsQueryDao;
import com.innospots.nexmux.core.stats.domain.vo.DashboardVo;
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

class DashboardServiceTest {

    private StatsQueryDao statsQueryDao;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        statsQueryDao = Mockito.mock(StatsQueryDao.class);
        service = new DashboardService(statsQueryDao);
    }

    @Test
    void dashboardAssemblesSummaryTrendAndDistributions() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("requests", 100L);
        summary.put("tokens", 390000L);
        summary.put("costUsd", new BigDecimal("1.2700"));
        summary.put("billedUsd", new BigDecimal("1.5200"));
        summary.put("successCount", 97L);
        summary.put("errorCount", 3L);
        summary.put("avgLatencyMs", 320.5);
        summary.put("activeClients", 8L);
        when(statsQueryDao.summarize(any(), any())).thenReturn(summary);
        when(statsQueryDao.trendByDay(any(), any())).thenReturn(List.of(
                Map.of("day", "2026-09-18", "requests", 60L, "tokens", 200000L,
                        "costUsd", new BigDecimal("0.70"), "avgLatencyMs", 300.0),
                Map.of("day", "2026-09-19", "requests", 40L, "tokens", 190000L,
                        "costUsd", new BigDecimal("0.57"), "avgLatencyMs", 340.0)));
        when(statsQueryDao.requestsByDimension(any(), any(), any())).thenReturn(List.of(
                Map.of("dim", "openai/o4-mini", "requests", 60L),
                Map.of("dim", "openai/gpt-5", "requests", 40L)));
        when(statsQueryDao.realtime(any(Integer.class))).thenReturn(Map.of(
                "requests", 128L, "tokens", 2100L));

        DashboardVo vo = service.dashboard(
                LocalDateTime.of(2026, 9, 18, 0, 0), LocalDateTime.of(2026, 9, 19, 23, 59));

        assertThat(vo.requests()).isEqualTo(100L);
        assertThat(vo.tokens()).isEqualTo(390000L);
        assertThat(vo.successRate()).isEqualTo(0.97);
        assertThat(vo.avgLatencyMs()).isEqualTo(320.5);
        assertThat(vo.activeClients()).isEqualTo(8L);
        assertThat(vo.trend()).hasSize(2);
        assertThat(vo.trend().get(0).t()).isEqualTo("2026-09-18");
        assertThat(vo.modelDistribution().get(0).name()).isEqualTo("openai/o4-mini");
        assertThat(vo.modelDistribution().get(0).requests()).isEqualTo(60L);
        assertThat(vo.realtimeRpm()).isEqualTo(128L);
        assertThat(vo.realtimeTokens()).isEqualTo(2100L);
    }

    @Test
    void dashboardHandlesEmptyRange() {
        Map<String, Object> empty = new java.util.HashMap<>();
        empty.put("requests", 0L);
        empty.put("tokens", 0L);
        empty.put("costUsd", BigDecimal.ZERO);
        empty.put("billedUsd", BigDecimal.ZERO);
        empty.put("successCount", 0L);
        empty.put("errorCount", 0L);
        empty.put("avgLatencyMs", 0.0);
        empty.put("activeClients", 0L);
        when(statsQueryDao.summarize(any(), any())).thenReturn(empty);
        when(statsQueryDao.trendByDay(any(), any())).thenReturn(List.of());
        when(statsQueryDao.requestsByDimension(any(), any(), any())).thenReturn(List.of());
        when(statsQueryDao.realtime(any(Integer.class))).thenReturn(Map.of("requests", 0L, "tokens", 0L));

        DashboardVo vo = service.dashboard(
                LocalDateTime.of(2026, 9, 18, 0, 0), LocalDateTime.of(2026, 9, 19, 23, 59));

        assertThat(vo.requests()).isZero();
        assertThat(vo.successRate()).isZero();
        assertThat(vo.trend()).isEmpty();
    }
}
