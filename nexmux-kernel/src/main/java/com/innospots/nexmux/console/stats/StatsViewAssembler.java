package com.innospots.nexmux.console.stats;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.shared.DisplayFormats;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.console.shared.LogTexts;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.stats.domain.vo.AvailabilityVo;
import com.innospots.nexmux.core.stats.domain.vo.DashboardVo;
import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import com.innospots.nexmux.core.stats.service.AvailabilityService;
import com.innospots.nexmux.core.stats.service.DashboardService;
import com.innospots.nexmux.core.stats.service.UsageStatsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Stats UI 投影（/api/d/stats-* 契约的装配点）：stats 原始聚合 → 展示形状，
 * 所有展示文案（fmt*）与 dashboard-mock / ops-usage-mock 逐字对齐。
 * model/provider/client/route 名称全量内存 join 补齐。
 */
@RequiredArgsConstructor
public class StatsViewAssembler {

    private final DashboardService dashboardService;
    private final UsageStatsService usageStatsService;
    private final AvailabilityService availabilityService;
    private final ModelService modelService;
    private final ProviderService providerService;
    private final ClientService clientService;
    private final RouteService routeService;
    private final DisplayFormatsProvider displayFormats;

    // ── Dashboard ──────────────────────────────────────────────

    /** 仪表盘聚合（{realtime,metricKeys,stats,trend,models,clients}）。 */
    public DashboardView dashboard(String start, String end) {
        DashboardVo vo = dashboardService.dashboard(rangeStart(start), rangeEnd(end));
        Relations relations = relations();
        DisplayFormats formats = displayFormats.current();
        double successPct = vo.successRate() * 100;
        return new DashboardView(
                new DashboardView.Realtime(vo.realtimeRpm(), fmtCompact(vo.realtimeTokens())),
                new DashboardView.MetricKeys(
                        List.of("requests"), List.of("tokens"), List.of("cost"), List.of("latency")),
                new DashboardView.Stats(
                        fmtInt(vo.requests()),
                        vo.activeClients(),
                        String.format(Locale.US, "%.1fM", vo.tokens() / 1_000_000.0),
                        String.format(Locale.US, "%.1f", successPct),
                        fmtInt(vo.errorCount()),
                        String.format(Locale.US, "%.2f", 100 - successPct),
                        Math.round(vo.avgLatencyMs()),
                        formats.money(vo.costUsd()),
                        formats.money(vo.requests() == 0
                                ? BigDecimal.ZERO
                                : vo.costUsd().divide(BigDecimal.valueOf(vo.requests()), 10, RoundingMode.HALF_UP))),
                vo.trend().stream()
                        .map(t -> new DashboardView.TrendPoint(
                                t.t() == null ? "" : t.t().substring(Math.min(5, t.t().length()), Math.min(10, t.t().length())),
                                t.requests(),
                                BigDecimal.valueOf(t.tokens())
                                        .divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP)
                                        .doubleValue(),
                                t.cost(),
                                Math.round(t.avgLatencyMs())))
                        .toList(),
                toDistribution(vo.modelDistribution(),
                        name -> relations.modelNameByCode.getOrDefault(name, name)),
                toDistribution(vo.clientDistribution(),
                        name -> relations.clientById.getOrDefault(name, new ClientLite(null, name, null)).name()));
    }

    private static List<DashboardView.Slice> toDistribution(
            List<DashboardVo.DimensionCount> rows, java.util.function.Function<String, String> nameOf) {
        long total = rows.stream().mapToLong(DashboardVo.DimensionCount::requests).sum();
        if (total == 0) {
            return List.of();
        }
        List<DashboardView.Slice> items = new ArrayList<>();
        rows.stream().limit(5).forEach(r -> items.add(new DashboardView.Slice(
                nameOf.apply(r.name()),
                Math.max(1, Math.round(r.requests() * 100.0 / total)))));
        long top = items.stream().mapToLong(DashboardView.Slice::percent).sum();
        items.add(new DashboardView.Slice("其他", Math.max(0, 100 - top)));
        return items;
    }

    // ── 用量行 ─────────────────────────────────────────────────

    /** 模型用量（{items,summary,total}）。 */
    public UsageListResult<ModelUsageRow> modelUsage(String start, String end) {
        List<UsageRowVo> rows = usageStatsService.usageByModel(rangeStart(start), rangeEnd(end));
        Relations relations = relations();
        DisplayFormats formats = displayFormats.current();
        long seconds = rangeSeconds(start, end);
        return new UsageListResult<>(
                rows.stream()
                        .map(row -> new ModelUsageRow(
                                row.key(),
                                relations.modelNameByCode.getOrDefault(row.key(), row.key()),
                                relations.routeNameByModel.getOrDefault(row.key(), "—"),
                                usageDisplayOf(row, seconds, formats)))
                        .toList(),
                summarize(rows, formats),
                rows.size());
    }

    /** 供应商用量（{items,summary,total}）。 */
    public UsageListResult<ProviderUsageRow> providerUsage(String start, String end) {
        List<UsageRowVo> rows = usageStatsService.usageByProvider(rangeStart(start), rangeEnd(end));
        Relations relations = relations();
        DisplayFormats formats = displayFormats.current();
        long seconds = rangeSeconds(start, end);
        return new UsageListResult<>(
                rows.stream()
                        .map(row -> new ProviderUsageRow(
                                row.key(),
                                relations.providerNameByCode.getOrDefault(row.key(), row.key()),
                                usageDisplayOf(row, seconds, formats)))
                        .toList(),
                summarize(rows, formats),
                rows.size());
    }

    /** 客户端用量（{items,summary,total}；quotaUsage 由 lifetime TOTAL_TOKEN 配额派生）。 */
    public UsageListResult<ClientUsageRow> clientUsage(String start, String end) {
        List<UsageRowVo> rows = usageStatsService.usageByClient(rangeStart(start), rangeEnd(end));
        Relations relations = relations();
        DisplayFormats formats = displayFormats.current();
        long seconds = rangeSeconds(start, end);
        return new UsageListResult<>(
                rows.stream()
                        .map(row -> {
                            ClientLite client = relations.clientById.get(row.key());
                            long used = row.inputTokens() + row.outputTokens();
                            return new ClientUsageRow(
                                    row.key(),
                                    client == null ? row.key() : client.name(),
                                    client == null ? row.key() : client.code(),
                                    client == null || client.appKeyId() == null ? "—" : client.appKeyId(),
                                    client != null && client.tokenLimit() != null
                                            ? fmtCompact(used) + "/" + fmtCompact(client.tokenLimit())
                                            : "—",
                                    usageDisplayOf(row, seconds, formats));
                        })
                        .toList(),
                summarize(rows, formats),
                rows.size());
    }

    // ── 可用性 ─────────────────────────────────────────────────

    /** 供应商可用性（{providers,compare,errors}）。 */
    public AvailabilityView availability(String start, String end) {
        AvailabilityVo vo = availabilityService.availability(rangeStart(start), rangeEnd(end));
        Relations relations = relations();
        DisplayFormats formats = displayFormats.current();
        return new AvailabilityView(
                vo.providers().stream()
                        .map(row -> new AvailabilityView.ProviderQualityRow(
                                row.key(),
                                relations.providerNameByCode.getOrDefault(row.key(), row.key()),
                                fmtCompact(row.errorCount()),
                                qualityDisplayOf(row, formats)))
                        .toList(),
                vo.compare().stream()
                        .map(row -> {
                            String[] parts = row.key().split("@", -1);
                            String modelCode = parts.length > 0 ? parts[0] : row.key();
                            String providerCode = parts.length > 1 ? parts[1] : "";
                            return new AvailabilityView.ModelProviderCompareRow(
                                    row.key(),
                                    relations.modelNameByCode.getOrDefault(modelCode, modelCode),
                                    relations.providerNameByCode.getOrDefault(providerCode, providerCode),
                                    qualityDisplayOf(row, formats));
                        })
                        .toList(),
                vo.recentErrors().stream()
                        .map(row -> new AvailabilityView.RecentErrorRow(
                                row.requestId(),
                                errorTime(row.requestedAt(), formats),
                                relations.modelNameByCode.getOrDefault(
                                                row.modelCode() == null ? "" : row.modelCode(),
                                                row.modelCode() == null ? "—" : row.modelCode())
                                        + " / "
                                        + relations.providerNameByCode.getOrDefault(
                                                row.providerCode() == null ? "" : row.providerCode(),
                                                row.providerCode() == null ? "—" : row.providerCode()),
                                row.errorCode() == null ? "—" : row.errorCode()))
                        .toList());
    }

    // ── 行展示整形 ─────────────────────────────────────────────

    private static UsageDisplay usageDisplayOf(UsageRowVo row, long rangeSeconds, DisplayFormats formats) {
        long tokens = row.inputTokens() + row.outputTokens();
        long cacheBase = row.inputTokens() + row.cacheReadTokens();
        return new UsageDisplay(
                fmtCompact(row.requests()),
                fmtCompact(row.inputTokens()),
                fmtInt(row.inputTokens()),
                fmtCompact(row.outputTokens()),
                fmtInt(row.outputTokens()),
                fmtPct(cacheBase == 0 ? 0 : (double) row.cacheReadTokens() / cacheBase),
                formats.money(row.standardUsd()),
                formats.money(row.billedUsd()),
                formats.money(row.costUsd()),
                formats.money(row.requests() == 0
                        ? BigDecimal.ZERO
                        : row.billedUsd().divide(BigDecimal.valueOf(row.requests()), 10, RoundingMode.HALF_UP)),
                fmtPct(row.successRate()),
                fmtMs(row.avgLatencyMs()),
                fmtMs(row.avgTtftMs()),
                fmtMs(row.avgUpstreamMs()),
                fmtCompact(rangeSeconds == 0 ? 0 : (double) tokens / rangeSeconds),
                fmtPct(row.failoverRate()),
                String.format(Locale.US, "%.2f", row.avgAttempts()));
    }

    private static AvailabilityView.QualityDisplay qualityDisplayOf(AvailabilityVo.QualityRow row, DisplayFormats formats) {
        return new AvailabilityView.QualityDisplay(
                fmtCompact(row.requests()),
                fmtPct(row.successRate()),
                fmtMs(row.avgLatencyMs()),
                fmtMs(row.avgUpstreamMs()),
                fmtPct(row.failoverRate()),
                String.format(Locale.US, "%.2f", row.avgAttempts()),
                formats.money(row.costUsd()),
                formats.money(row.billedUsd()),
                formats.money(row.costUsd()));
    }

    private static UsageListResult.UsageSummary summarize(List<UsageRowVo> rows, DisplayFormats formats) {
        BigDecimal standard = rows.stream().map(UsageRowVo::standardUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal billed = rows.stream().map(UsageRowVo::billedUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = rows.stream().map(UsageRowVo::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UsageListResult.UsageSummary(formats.money(standard), formats.money(billed), formats.money(cost));
    }

    // ── 关联数据 ───────────────────────────────────────────────

    private record ClientLite(String code, String name, String appKeyId, Long tokenLimit) {
        ClientLite(String code, String name, String appKeyId) {
            this(code, name, appKeyId, null);
        }
    }

    private record Relations(
            Map<String, String> modelNameByCode,
            Map<String, String> providerNameByCode,
            Map<String, ClientLite> clientById,
            Map<String, String> routeNameByModel) {
    }

    private Relations relations() {
        Map<String, String> modelNameByCode = new LinkedHashMap<>();
        for (ModelVo model : modelService.listAll()) {
            modelNameByCode.put(model.modelCode(), model.displayName());
        }
        Map<String, String> providerNameByCode = new LinkedHashMap<>();
        for (ProviderVo provider : providerService.listAll()) {
            providerNameByCode.put(provider.providerCode(), provider.displayName());
        }
        Map<String, ClientLite> clientById = new LinkedHashMap<>();
        for (ClientVo client : clientService.listAll()) {
            clientById.put(client.clientId(), new ClientLite(
                    client.clientCode(), client.displayName(), client.appKeyId(),
                    lifetimeTokenLimit(client.quotaSpec())));
        }
        Map<String, String> routeNameByModel = new LinkedHashMap<>();
        for (RouteVo route : routeService.listAll()) {
            if (route.routeSpec() == null) {
                continue;
            }
            route.routeSpec().candidates().forEach(c ->
                    routeNameByModel.putIfAbsent(c.modelCode(), route.displayName()));
        }
        return new Relations(modelNameByCode, providerNameByCode, clientById, routeNameByModel);
    }

    /** quota_spec 中 lifetime TOTAL_TOKEN 规则的上限（客户端配额用量展示用）。 */
    private static Long lifetimeTokenLimit(String quotaSpec) {
        JsonNode spec = LogTexts.readJson(quotaSpec);
        if (spec == null || !spec.isArray()) {
            return null;
        }
        for (JsonNode rule : spec) {
            if ("lifetime".equals(textOrNull(rule, "quotaType"))
                    && "TOTAL_TOKEN".equals(textOrNull(rule, "metric"))
                    && rule.has("limit") && rule.get("limit").isNumber()) {
                return rule.get("limit").asLong();
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    // ── 格式化（与 mock 逐字对齐） ─────────────────────────────

    static String fmtInt(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    static String fmtCompact(double n) {
        if (n >= 1e9) {
            return String.format(Locale.US, "%.1fB", n / 1e9);
        }
        if (n >= 1e6) {
            return String.format(Locale.US, "%.1fM", n / 1e6);
        }
        if (n >= 1e3) {
            return String.format(Locale.US, "%.1fK", n / 1e3);
        }
        return String.valueOf(Math.round(n));
    }

    /** ISO 时间串 → 配置时区短格式（解析失败回退原文截取）。 */
    private static String errorTime(String iso, DisplayFormats formats) {
        if (iso == null) {
            return "—";
        }
        try {
            return formats.shortTime(LocalDateTime.parse(iso)) + " " + formats.zoneLabel();
        } catch (Exception e) {
            return iso.substring(5, Math.min(19, iso.length())).replace("T", " ") + " " + formats.zoneLabel();
        }
    }

    private static String fmtPct(double x) {
        return String.format(Locale.US, "%.1f%%", x * 100);
    }

    private static String fmtMs(double n) {
        return Math.round(n) + "ms";
    }

    /** 与端点一致的日期范围解析（缺省近 7 天）。 */
    static LocalDateTime rangeStart(String start) {
        if (start == null || start.isBlank()) {
            return LocalDate.now().minusDays(6).atStartOfDay();
        }
        return LocalDate.parse(start.trim()).atStartOfDay();
    }

    static LocalDateTime rangeEnd(String end) {
        if (end == null || end.isBlank()) {
            return LocalDate.now().atTime(LocalTime.MAX);
        }
        return LocalDate.parse(end.trim()).atTime(LocalTime.MAX);
    }

    private static long rangeSeconds(String start, String end) {
        long s = start == null || start.isBlank()
                ? 0 : LocalDate.parse(start.trim()).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long e = end == null || end.isBlank()
                ? System.currentTimeMillis() / 1000
                : LocalDate.parse(end.trim()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC);
        return Math.max(1, e - s);
    }

    // ── 视图记录 ───────────────────────────────────────────────

    /** 用量行展示字段（@JsonUnwrapped 平铺进各维度行）。 */
    public record UsageDisplay(
            String requests,
            String inputCompact,
            String inputRaw,
            String outputCompact,
            String outputRaw,
            String cacheHit,
            String standard,
            String billed,
            String cost,
            String avgBilled,
            String successRate,
            String avgLatency,
            String ttft,
            String avgUpstream,
            String tps,
            String failoverRate,
            String avgAttempts) {
    }

    /** 模型用量行。 */
    public record ModelUsageRow(
            String key,
            String model,
            String route,
            @JsonUnwrapped UsageDisplay display) {
    }

    /** 供应商用量行。 */
    public record ProviderUsageRow(
            String key,
            String provider,
            @JsonUnwrapped UsageDisplay display) {
    }

    /** 客户端用量行。 */
    public record ClientUsageRow(
            String key,
            String name,
            String code,
            String appKeyId,
            String quotaUsage,
            @JsonUnwrapped UsageDisplay display) {
    }
}
