package com.innospots.nexmux.console.route;

import com.innospots.nexmux.console.route.RouteRecord.ModelEntry;
import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.console.shared.StrategyLabels;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.console.shared.DisplayFormats;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Route UI 投影（/api/d/routes 契约的装配点）：核心域 VO → 展示记录，
 * 类型派生（候选数）/ 协议映射 / 模型行内存关联 / counts / filters 全部在此完成。
 * 构成口径基于全量（P0 数据量小），不随当前筛选变化。
 */
@RequiredArgsConstructor
public class RouteViewAssembler {

    /** 类型展示文案（type 由候选数派生，P0 无 conditional 产出）。 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "single", "单模型",
            "pool", "多模型池",
            "conditional", "条件路由");

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "enabled", "启用",
            "disabled", "停用",
            "draft", "草稿");

    /** 协议格式 → Ingress Operation。 */
    private static final Map<String, String> PROTOCOL_OPERATIONS = Map.of(
            "OpenAI / Chat Completions", "chat.completions",
            "OpenAI / Responses", "responses",
            "Anthropic / Messages", "messages",
            "OpenAI / Embeddings", "embeddings");

    private static final List<String> TYPE_CHIP_ORDER = List.of("single", "pool", "conditional");


    private final RouteService routeService;
    private final ModelService modelService;
    private final DisplayFormatsProvider displayFormats;

    /**
     * 分页列表：items 为当前页，counts/filters 为全量构成口径。
     */
    public RouteListResult assemble(RoutePageRequest request) {
        PageResult<RouteVo> page = routeService.page(request);
        Map<String, ModelVo> modelsByCode = modelsByCode();
        DisplayFormats formats = displayFormats.current();

        List<RouteRecord> items = page.records().stream()
                .map(vo -> toRecord(vo, null, modelsByCode, formats))
                .toList();
        List<RouteRecord> universe = routeService.listAll().stream()
                .map(vo -> toRecord(vo, null, modelsByCode, formats))
                .toList();

        RouteListResult.Counts counts = countsOf(universe);
        return new RouteListResult(
                items, page.total(), page.pageNo(), page.pageSize(), page.pages(),
                counts, filtersOf(counts, universe));
    }

    /**
     * 按稳定业务键查询详情。
     */
    public RouteRecord detail(String routeCode) {
        RouteDetailVo detail = routeService.getByCode(routeCode);
        return toRecord(baseViewOf(detail), detail, modelsByCode(), displayFormats.current());
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static RouteVo baseViewOf(RouteDetailVo d) {
        return new RouteVo(
                d.routeId(), d.routeCode(), d.alias(), d.displayName(), d.status(),
                d.routeSpec(), d.publishedAt());
    }

    private Map<String, ModelVo> modelsByCode() {
        Map<String, ModelVo> modelsByCode = new LinkedHashMap<>();
        for (ModelVo model : modelService.listAll()) {
            modelsByCode.put(model.modelCode(), model);
        }
        return modelsByCode;
    }

    private static RouteRecord toRecord(RouteVo vo, RouteDetailVo detail, Map<String, ModelVo> modelsByCode,
            DisplayFormats formats) {
        RouteSpec spec = vo.routeSpec();
        List<RouteCandidate> candidates = spec == null ? List.of() : spec.candidates();
        List<ModelEntry> models = candidates.stream()
                .map(c -> modelEntryOf(c, modelsByCode))
                .toList();
        String type = candidates.size() > 1 ? "pool" : "single";
        String status = statusOf(vo.status());
        String strategy = spec == null || spec.strategyType() == null ? "FIXED" : spec.strategyType();
        String strategyLabel = StrategyLabels.of(strategy);
        String path = spec == null ? "" : nullToEmpty(spec.ingress());
        String protocol = spec == null ? "" : nullToEmpty(spec.protocol());
        List<String> tags = spec == null ? List.of() : spec.tags();
        long enabledModelCount = models.stream().filter(m -> "enabled".equals(m.status())).count();
        long weightTotal = models.stream()
                .filter(m -> "enabled".equals(m.status()))
                .mapToLong(ModelEntry::weight).sum();
        String updatedAt = detail == null ? "" : formats.dayMinuteTime(detail.updatedAt());
        // 草稿与发布快照不一致 → 存在未发布变更（详情页提示用）
        boolean hasUnpublishedChanges = detail != null
                && detail.publishedRouteSpec() != null
                && !detail.routeSpec().equals(detail.publishedRouteSpec());

        return new RouteRecord(
                vo.routeId(),
                vo.routeCode(),
                vo.displayName(),
                type,
                TYPE_LABELS.getOrDefault(type, type),
                vo.alias(),
                tags,
                spec == null ? "" : nullToEmpty(spec.description()),
                status,
                STATUS_LABELS.getOrDefault(status, status),
                path,
                protocol,
                PROTOCOL_OPERATIONS.getOrDefault(protocol, "—"),
                strategy,
                strategyLabel,
                spec != null && Boolean.TRUE.equals(spec.stickySession()),
                spec == null || spec.filterUnhealthyBindings(),
                models,
                detail == null ? "" : formats.dayMinuteTime(detail.createdAt()),
                updatedAt,
                detail == null || detail.updatedBy() == null ? "—" : detail.updatedBy(),
                path + " · " + protocol,
                models.size() + " 模型 · " + strategyLabel,
                strategyLabel + " (" + strategy + ")",
                String.join(" · ", tags),
                enabledModelCount,
                weightTotal,
                detail == null || detail.updatedAt() == null ? "" : formats.shortDate(detail.updatedAt()),
                hasUnpublishedChanges);
    }

    private static ModelEntry modelEntryOf(RouteCandidate candidate, Map<String, ModelVo> modelsByCode) {
        ModelVo model = modelsByCode.get(candidate.modelCode());
        return new ModelEntry(
                candidate.modelCode(),
                model == null ? candidate.modelCode() : model.displayName(),
                candidate.weight(),
                candidate.priority() == null ? "P0" : candidate.priority(),
                candidate.costRate(),
                candidate.billingRate(),
                Boolean.FALSE.equals(candidate.enabled()) ? "disabled" : "enabled");
    }

    private static String statusOf(String backendStatus) {
        if ("PUBLISHED".equals(backendStatus)) {
            return "enabled";
        }
        if ("DISABLED".equals(backendStatus)) {
            return "disabled";
        }
        return "draft";
    }

    private static RouteListResult.Counts countsOf(List<RouteRecord> universe) {
        long enabled = universe.stream().filter(r -> "enabled".equals(r.status())).count();
        Map<String, EnabledTotal> types = new LinkedHashMap<>();
        Map<String, Long> tags = new LinkedHashMap<>();
        for (String type : TYPE_CHIP_ORDER) {
            List<RouteRecord> inType = universe.stream().filter(r -> r.type().equals(type)).toList();
            types.put(type, new EnabledTotal(
                    inType.stream().filter(r -> "enabled".equals(r.status())).count(), inType.size()));
        }
        for (RouteRecord item : universe) {
            for (String tag : item.tags()) {
                tags.merge(tag.replace('-', '_'), 1L, Long::sum);
            }
        }
        return new RouteListResult.Counts(
                new EnabledTotal(enabled, universe.size()),
                new RouteListResult.StatusCounts(
                        enabled,
                        universe.stream().filter(r -> "disabled".equals(r.status())).count(),
                        universe.stream().filter(r -> "draft".equals(r.status())).count()),
                types, tags);
    }

    private static RouteListResult.Filters filtersOf(
            RouteListResult.Counts counts, List<RouteRecord> universe) {
        List<FilterChip> statusChips = List.of(
                new FilterChip("all", "全部 " + counts.all().enabled() + "/" + counts.all().total(), null),
                new FilterChip("enabled", "已启用 " + counts.status().enabled(), null),
                new FilterChip("disabled", "已停用 " + counts.status().disabled(), null),
                new FilterChip("draft", "草稿 " + counts.status().draft(), null));
        List<FilterChip> typeChips = new ArrayList<>();
        typeChips.add(new FilterChip(
                "all", "全部 " + counts.all().enabled() + "/" + counts.all().total(), null));
        for (String type : TYPE_CHIP_ORDER) {
            EnabledTotal stat = counts.types().get(type);
            typeChips.add(new FilterChip(
                    type, TYPE_LABELS.get(type) + " " + stat.enabled() + "/" + stat.total(), null));
        }
        // 标签 chip 按全量首现序
        List<String> tagOrder = new ArrayList<>();
        for (RouteRecord item : universe) {
            for (String tag : item.tags()) {
                if (!tagOrder.contains(tag)) {
                    tagOrder.add(tag);
                }
            }
        }
        List<FilterChip> tagChips = new ArrayList<>();
        tagChips.add(new FilterChip("all", "全部 " + counts.all().total(), null));
        for (String tag : tagOrder) {
            tagChips.add(new FilterChip(
                    tag, tag + " " + counts.tags().getOrDefault(tag.replace('-', '_'), 0L), null));
        }
        return new RouteListResult.Filters(statusChips, typeChips, tagChips);
    }



    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
