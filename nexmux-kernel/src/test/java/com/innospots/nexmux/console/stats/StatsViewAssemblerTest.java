package com.innospots.nexmux.console.stats;

import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.stats.domain.vo.AvailabilityVo;
import com.innospots.nexmux.core.stats.domain.vo.DashboardVo;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import com.innospots.nexmux.core.stats.service.AvailabilityService;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.stats.service.DashboardService;
import com.innospots.nexmux.core.stats.service.UsageStatsService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link StatsViewAssembler} 单测：dashboard 汇总/分布、用量行展示整形、
 * 客户端配额用量派生、可用性组合键拆分。
 */
class StatsViewAssemblerTest {

    private DashboardService dashboardService;
    private UsageStatsService usageStatsService;
    private AvailabilityService availabilityService;
    private StatsViewAssembler assembler;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(DashboardService.class);
        usageStatsService = Mockito.mock(UsageStatsService.class);
        availabilityService = Mockito.mock(AvailabilityService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        ProviderService providerService = Mockito.mock(ProviderService.class);
        ClientService clientService = Mockito.mock(ClientService.class);
        RouteService routeService = Mockito.mock(RouteService.class);
        assembler = new StatsViewAssembler(dashboardService, usageStatsService, availabilityService,
                modelService, providerService, clientService, routeService, DisplayFormatsProvider.defaults());

        when(modelService.listAll()).thenReturn(List.of(
                new ModelVo("m1", "zai/glm-5", "GLM-5", "ENABLED", "LLM",
                        null, null, "pv1", null, null)));
        when(providerService.listAll()).thenReturn(List.of(
                new ProviderVo("pv1", "zai", "Z.AI", "COMMERCIAL", "openai-completions",
                        "zai", "zai", null, "https://api.z.ai", null, 60000, "ENABLED", "none", 0)));
        when(clientService.listAll()).thenReturn(List.of(
                new ClientVo("c1", "app-web", "Web 应用", "ENABLED", List.of(),
                        "[{\"quotaType\":\"lifetime\",\"metric\":\"TOTAL_TOKEN\",\"limit\":10000000}]",
                        "[]", 1, "ak-1", "[]", null, null, null)));
        when(routeService.listAll()).thenReturn(List.of(
                new RouteVo("r1", "general", "general", "通用对话", "PUBLISHED",
                        new RouteSpec("general", "/v1", "WEIGHTED",
                                List.of(new RouteCandidate("zai/glm-5", 100)), true, 1), null)));
    }

    private static UsageRowVo usageRow(String key) {
        return new UsageRowVo(key, 12500, 25, 2_000_000, 500_000, 400_000,
                new BigDecimal("30.00"), new BigDecimal("36.00"), new BigDecimal("24.00"),
                0.998, 820.0, 95.0, 640.0, 0.002, 1.02);
    }

    @Test
    void assemblesDashboardCardsTrendAndDistribution() {
        when(dashboardService.dashboard(any(), any())).thenReturn(new DashboardVo(
                12345, 8_600_000, new BigDecimal("12.3456"), new BigDecimal("15.0"),
                0.987, 160, 820.0, 3,
                List.of(new DashboardVo.TrendPoint("2026-10-05", 5000, 2_500_000,
                        new BigDecimal("3.5"), 810.0)),
                List.of(new DashboardVo.DimensionCount("zai/glm-5", 9000),
                        new DashboardVo.DimensionCount("other-model", 3345)),
                List.of(new DashboardVo.DimensionCount("c1", 12345)),
                42, 86000));

        DashboardView view = assembler.dashboard("2026-10-01", "2026-10-07");

        assertThat(view.realtime().rpm()).isEqualTo(42);
        assertThat(view.realtime().tpm()).isEqualTo("86.0K");
        assertThat(view.stats().requests()).isEqualTo("12,345");
        assertThat(view.stats().tokens()).isEqualTo("8.6M");
        assertThat(view.stats().successRate()).isEqualTo("98.7");
        assertThat(view.stats().errorRate()).isEqualTo("1.30");
        assertThat(view.stats().failures()).isEqualTo("160");
        assertThat(view.stats().avgLatency()).isEqualTo(820);
        assertThat(view.stats().cost()).isEqualTo("$12.3456");
        assertThat(view.stats().avgCost()).isEqualTo("$0.0010");
        assertThat(view.metricKeys().requests()).containsExactly("requests");
        assertThat(view.trend()).hasSize(1);
        assertThat(view.trend().get(0).t()).isEqualTo("10-05");
        assertThat(view.trend().get(0).tokens()).isEqualTo(2.5);
        assertThat(view.trend().get(0).latency()).isEqualTo(810);
        assertThat(view.models()).hasSize(3);
        assertThat(view.models().get(0).name()).isEqualTo("GLM-5");
        assertThat(view.models().get(0).percent()).isEqualTo(73);
        assertThat(view.models().get(2).name()).isEqualTo("其他");
        assertThat(view.clients().get(0).name()).isEqualTo("Web 应用");
    }

    @Test
    void assemblesModelUsageRowsWithDisplayTexts() {
        when(usageStatsService.usageByModel(any(), any())).thenReturn(List.of(usageRow("zai/glm-5")));

        UsageListResult<StatsViewAssembler.ModelUsageRow> result =
                assembler.modelUsage("2026-10-01", "2026-10-07");

        assertThat(result.total()).isEqualTo(1);
        StatsViewAssembler.ModelUsageRow row = result.items().get(0);
        assertThat(row.model()).isEqualTo("GLM-5");
        assertThat(row.route()).isEqualTo("通用对话");
        assertThat(row.display().requests()).isEqualTo("12.5K");
        assertThat(row.display().inputCompact()).isEqualTo("2.0M");
        assertThat(row.display().cacheHit()).isEqualTo("16.7%");
        assertThat(row.display().standard()).isEqualTo("$30.0000");
        assertThat(row.display().billed()).isEqualTo("$36.0000");
        assertThat(row.display().cost()).isEqualTo("$24.0000");
        assertThat(row.display().avgBilled()).isEqualTo("$0.0029");
        assertThat(row.display().successRate()).isEqualTo("99.8%");
        assertThat(row.display().avgLatency()).isEqualTo("820ms");
        assertThat(row.display().ttft()).isEqualTo("95ms");
        assertThat(row.display().avgAttempts()).isEqualTo("1.02");
        assertThat(row.display().tps()).isNotBlank();
        assertThat(result.summary().standard()).isEqualTo("$30.0000");
    }

    @Test
    void derivesClientQuotaUsageFromLifetimeTotalTokenLimit() {
        when(usageStatsService.usageByClient(any(), any())).thenReturn(List.of(usageRow("c1")));

        UsageListResult<StatsViewAssembler.ClientUsageRow> result =
                assembler.clientUsage("2026-10-01", "2026-10-07");

        StatsViewAssembler.ClientUsageRow row = result.items().get(0);
        assertThat(row.name()).isEqualTo("Web 应用");
        assertThat(row.code()).isEqualTo("app-web");
        // 已用 2.5M / 上限 10M
        assertThat(row.quotaUsage()).isEqualTo("2.5M/10.0M");
    }

    @Test
    void assemblesAvailabilityWithSplitKeysAndRecentErrors() {
        when(availabilityService.availability(any(), any())).thenReturn(new AvailabilityVo(
                List.of(new AvailabilityVo.QualityRow("zai", 12500, 25, 0.998, 820.0, 640.0,
                        0.002, 1.02, new BigDecimal("36.00"), new BigDecimal("24.00"))),
                List.of(new AvailabilityVo.QualityRow("zai/glm-5@zai", 12500, 25, 0.998, 820.0, 640.0,
                        0.002, 1.02, new BigDecimal("36.00"), new BigDecimal("24.00"))),
                List.of(new AvailabilityVo.RecentError(
                        "req-1", "2026-10-05T08:00:00", "zai/glm-5", "zai", "UPSTREAM_5XX"))));

        AvailabilityView view = assembler.availability("2026-10-01", "2026-10-07");

        assertThat(view.providers()).hasSize(1);
        assertThat(view.providers().get(0).provider()).isEqualTo("Z.AI");
        assertThat(view.providers().get(0).errors()).isEqualTo("25");
        assertThat(view.providers().get(0).display().successRate()).isEqualTo("99.8%");
        assertThat(view.compare().get(0).model()).isEqualTo("GLM-5");
        assertThat(view.compare().get(0).provider()).isEqualTo("Z.AI");
        assertThat(view.errors().get(0).time()).isEqualTo("10-05 08:00:00 UTC+8");
        assertThat(view.errors().get(0).target()).isEqualTo("GLM-5 / Z.AI");
        assertThat(view.errors().get(0).error()).isEqualTo("UPSTREAM_5XX");
    }
}
