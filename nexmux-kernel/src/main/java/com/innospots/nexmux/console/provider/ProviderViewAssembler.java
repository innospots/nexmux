package com.innospots.nexmux.console.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.console.shared.VendorIcons;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.domain.vo.UpstreamModelVo;
import com.innospots.nexmux.console.shared.DisplayFormats;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexus.base.domain.response.PageResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Provider UI 投影（/api/d/providers 契约的装配点）：核心域 VO → 展示记录，
 * 派生文案 / 关联统计（model、route 内存关联）/ counts / filters 全部在此完成。
 * 构成口径基于全量（P0 数据量小），不随当前筛选变化。
 */
@RequiredArgsConstructor
public class ProviderViewAssembler {


    private final ProviderService providerService;
    private final ModelService modelService;
    private final RouteService routeService;
    private final DisplayFormatsProvider displayFormats;

    /**
     * 分页列表：items 为当前页，counts/filters 为全量构成口径。
     */
    public ProviderListResult assemble(ProviderPageRequest request) {
        PageResult<ProviderVo> page = providerService.page(request);
        Relations relations = Relations.load(modelService.listAll(), routeService.listAll());

        List<ProviderRecord> items = page.records().stream()
                .map(vo -> toRecord(vo, relations, null))
                .toList();
        List<ProviderRecord> universe = providerService.listAll().stream()
                .map(vo -> toRecord(vo, relations, null))
                .toList();

        ProviderListResult.Counts counts = countsOf(universe);
        return new ProviderListResult(
                items, page.total(), page.pageNo(), page.pageSize(), page.pages(),
                counts, filtersOf(counts, universe));
    }

    /**
     * 按稳定业务键查询详情（含密钥版本、关联模型行与能力标签）。
     */
    public ProviderRecord detail(String providerCode) {
        ProviderDetailVo detail = providerService.getByCode(providerCode);
        Relations relations = Relations.load(modelService.listAll(), routeService.listAll());
        return toRecord(baseViewOf(detail), relations, detail);
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static ProviderVo baseViewOf(ProviderDetailVo d) {
        return new ProviderVo(
                d.providerId(), d.providerCode(), d.displayName(), d.providerType(),
                d.defaultProtocol(), d.vendorCode(), d.sourceVendorCode(), d.iconUri(),
                d.baseUrl(), d.region(), d.timeoutMs(), d.status(), d.keyStatus(),
                d.models() == null ? 0 : d.models().size());
    }

    /**
     * 拉取上游模型清单（code → providerId 解析后委托核心域）。
     */
    public List<PullModelRow> pullModels(String providerCode) {
        ProviderDetailVo detail = providerService.getByCode(providerCode);
        return providerService.pullModels(detail.providerId()).stream()
                .map(ProviderViewAssembler::toPullRow)
                .toList();
    }

    /**
     * 表单 Provider 下拉选项（模型新建/编辑页用）。
     */
    public List<ProviderOption> options() {
        return providerService.listAll().stream()
                .map(p -> new ProviderOption(p.displayName(), p.providerCode()))
                .toList();
    }

    private static PullModelRow toPullRow(UpstreamModelVo row) {
        return new PullModelRow(
                row.providerModelId(), row.providerModelId(), row.existsLocally() ? "已存在" : "新增");
    }

    private ProviderRecord toRecord(ProviderVo vo, Relations relations, ProviderDetailVo detail) {
        DisplayFormats formats = displayFormats.current();
        List<ModelVo> ownModels = relations.modelsByProvider.getOrDefault(vo.providerId(), List.of());
        ProviderRecord.RouteStat routes =
                relations.routesByProvider.getOrDefault(vo.providerId(), new ProviderRecord.RouteStat(0, 0));

        List<ProviderRecord.ModelRow> modelRows = detail == null
                ? List.of()
                : detail.models().stream()
                        .map(m -> new ProviderRecord.ModelRow(
                                m.modelCode(),
                                m.modelCode().replace("/", "~"),
                                m.displayName(),
                                nullToEmpty(m.modelType()),
                                nullToEmpty(m.providerModelId()),
                                "—",
                                "ENABLED".equals(m.status()) ? "ONLINE" : "OFFLINE",
                                relations.routeCountByModel.getOrDefault(m.modelCode(), 0L)))
                        .toList();
        long enabledModelCount = detail == null
                ? ownModels.stream().filter(m -> "ENABLED".equals(m.status())).count()
                : modelRows.stream().filter(m -> "ONLINE".equals(m.status())).count();

        return new ProviderRecord(
                vo.providerCode(),
                vo.displayName(),
                vo.displayName(),
                VendorIcons.iconOf(vo.vendorCode()),
                nullToEmpty(vo.providerType()),
                nullToEmpty(vo.defaultProtocol()),
                nullToEmpty(vo.vendorCode()),
                "ENABLED".equals(vo.status()) ? "enabled" : "disabled",
                vo.keyStatus() == null ? "none" : vo.keyStatus(),
                nullToEmpty(vo.baseUrl()),
                nullToEmpty(vo.region()),
                vo.timeoutMs() == null ? 0 : vo.timeoutMs(),
                detail == null ? "" : nullToEmpty(detail.proxy()),
                maskedKeyOf(vo.keyStatus(), detail == null ? null : detail.keyVersion()),
                null,
                capabilitiesOf(detail == null ? null : detail.capabilityExtension()),
                routes,
                routeSummaryOf(routes),
                modelRows,
                detail == null ? vo.modelCount() : modelRows.size(),
                enabledModelCount,
                detail == null ? "" : nullToEmpty(detail.description()),
                detail == null ? "" : nullToEmpty(detail.capabilityExtension()),
                detail == null ? "" : formats.dayMinuteTime(detail.createdAt()),
                detail == null ? "" : formats.dayMinuteTime(detail.updatedAt()),
                detail == null || detail.updatedBy() == null ? "—" : detail.updatedBy());
    }

    private static String maskedKeyOf(String keyStatus, Integer keyVersion) {
        if ("configured".equals(keyStatus)) {
            return "••••••••" + (keyVersion == null ? "" : " (v" + keyVersion + ")");
        }
        if ("pending".equals(keyStatus)) {
            return "待配置";
        }
        return "—";
    }

    /** capabilityExtension JSON → 能力标签数组（容忍数组 / 对象 / 非法文本）。 */
    private static List<String> capabilitiesOf(String capabilityExtension) {
        JsonNode node = readJsonTolerant(capabilityExtension);
        if (node == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    out.add(item.asText());
                }
            });
        } else if (node.isObject()) {
            node.fieldNames().forEachRemaining(out::add);
        }
        return out;
    }

    private static String routeSummaryOf(ProviderRecord.RouteStat routes) {
        return routes.total() == 0
                ? "未关联路由"
                : routes.total() + " 条路由 · " + routes.enabled() + " 条启用";
    }


    private static JsonNode readJsonTolerant(String json) {
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ProviderListResult.Counts countsOf(List<ProviderRecord> universe) {
        long enabled = universe.stream().filter(p -> "enabled".equals(p.status())).count();
        Map<String, EnabledTotal> types = new LinkedHashMap<>();
        for (ProviderRecord item : universe) {
            EnabledTotal stat = types.get(item.family());
            types.put(item.family(), new EnabledTotal(
                    (stat == null ? 0 : stat.enabled()) + ("enabled".equals(item.status()) ? 1 : 0),
                    (stat == null ? 0 : stat.total()) + 1));
        }
        return new ProviderListResult.Counts(
                new EnabledTotal(enabled, universe.size()),
                new ProviderListResult.StatusCounts(
                        enabled,
                        universe.stream().filter(p -> "disabled".equals(p.status())).count(),
                        universe.stream().filter(p -> "pending".equals(p.keyStatus())).count(),
                        universe.stream().filter(p -> "none".equals(p.keyStatus())).count()),
                types);
    }

    private static ProviderListResult.Filters filtersOf(
            ProviderListResult.Counts counts, List<ProviderRecord> universe) {
        // 厂商 label 取该族第一个实例的 displayName；按总数降序（稳定排序，并列保持先入序）
        Map<String, String> labelByFamily = new LinkedHashMap<>();
        for (ProviderRecord item : universe) {
            labelByFamily.putIfAbsent(item.family(), item.displayName());
        }
        List<FilterChip> typeChips = counts.types().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(EnabledTotal::total).reversed()))
                .map(e -> new FilterChip(
                        e.getKey(),
                        labelByFamily.getOrDefault(e.getKey(), e.getKey())
                                + " " + e.getValue().enabled() + "/" + e.getValue().total(),
                        VendorIcons.dotOf(e.getKey())))
                .toList();
        return new ProviderListResult.Filters(
                List.of(
                        new FilterChip("all", "全部 " + counts.all().enabled() + "/" + counts.all().total(), null),
                        new FilterChip("enabled", "已启用 " + counts.status().enabled(), "#22c55e"),
                        new FilterChip("disabled", "已停用 " + counts.status().disabled(), "#9ca3af"),
                        new FilterChip("pending", "待填密钥 " + counts.status().pending(), "#f59e0b"),
                        new FilterChip("none", "无密钥 " + counts.status().none(), "#ef4444")),
                typeChips);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 关联统计：model / route 全量内存关联（P0 数据量小）。
     * route → provider 经候选 modelCode → 主提供方反解。
     */
    private record Relations(
            Map<String, List<ModelVo>> modelsByProvider,
            Map<String, ProviderRecord.RouteStat> routesByProvider,
            Map<String, Long> routeCountByModel) {

        static Relations load(List<ModelVo> models, List<RouteVo> routes) {
            Map<String, List<ModelVo>> modelsByProvider = new LinkedHashMap<>();
            Map<String, String> providerByModel = new LinkedHashMap<>();
            for (ModelVo model : models) {
                String providerId = model.primaryProviderId() == null ? "" : model.primaryProviderId();
                modelsByProvider.computeIfAbsent(providerId, k -> new ArrayList<>()).add(model);
                providerByModel.put(model.modelCode(), providerId);
            }
            Map<String, ProviderRecord.RouteStat> routesByProvider = new LinkedHashMap<>();
            Map<String, Long> routeCountByModel = new LinkedHashMap<>();
            for (RouteVo route : routes) {
                boolean published = "PUBLISHED".equals(route.status());
                route.routeSpec().candidates().stream()
                        .map(c -> providerByModel.getOrDefault(c.modelCode(), ""))
                        .filter(id -> !id.isEmpty())
                        .distinct()
                        .forEach(providerId -> {
                            ProviderRecord.RouteStat stat = routesByProvider.get(providerId);
                            routesByProvider.put(providerId, new ProviderRecord.RouteStat(
                                    (stat == null ? 0 : stat.total()) + 1,
                                    (stat == null ? 0 : stat.enabled()) + (published ? 1 : 0)));
                        });
                route.routeSpec().candidates().forEach(c ->
                        routeCountByModel.merge(c.modelCode(), 1L, Long::sum));
            }
            return new Relations(modelsByProvider, routesByProvider, routeCountByModel);
        }
    }
}
