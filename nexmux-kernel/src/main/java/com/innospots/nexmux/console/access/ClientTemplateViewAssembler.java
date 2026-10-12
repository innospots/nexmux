package com.innospots.nexmux.console.access;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.console.shared.DisplayFormats;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * ClientTemplate UI 投影（/api/d/client-templates 契约的装配点）：核心域 VO →
 * 展示记录，有效期文案 / 授权路由关联 / 配额限流整形 / counts / filters 全部在此完成。
 * 构成口径基于全量（P0 数据量小），不随当前筛选变化。
 */
@RequiredArgsConstructor
public class ClientTemplateViewAssembler {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "enabled", "启用",
            "disabled", "停用");


    private final ClientTemplateService clientTemplateService;
    private final RouteService routeService;
    private final ClientViewAssembler clientViewAssembler;
    private final DisplayFormatsProvider displayFormats;

    /**
     * 分页列表：items 为当前页，counts/filters 为全量构成口径。
     */
    public ClientTemplateListResult assemble(ClientTemplatePageRequest request) {
        PageResult<ClientTemplateVo> page = clientTemplateService.page(request);
        Map<String, RouteVo> routesById = routesById();

        List<ClientTemplateRecord> items = page.records().stream()
                .map(vo -> toRecord(vo, null, routesById))
                .toList();
        List<ClientTemplateRecord> universe = clientTemplateService.listAll().stream()
                .map(vo -> toRecord(vo, null, routesById))
                .toList();

        ClientTemplateListResult.Counts counts = countsOf(universe);
        return new ClientTemplateListResult(
                items, page.total(), page.pageNo(), page.pageSize(), page.pages(),
                counts, filtersOf(counts, universe));
    }

    /**
     * 按稳定业务键查询详情。
     */
    public ClientTemplateRecord detail(String templateCode) {
        ClientTemplateDetailVo detail = clientTemplateService.getByCode(templateCode);
        return toRecord(baseViewOf(detail), detail, routesById());
    }

    /**
     * 基于模板创建的客户端（code → templateId 解析后关联，复用 Client 投影）。
     */
    public List<ClientRecord> createdClients(String templateCode) {
        ClientTemplateDetailVo detail = clientTemplateService.getByCode(templateCode);
        return clientTemplateService.createdClients(detail.templateId()).stream()
                .map(ClientVo::clientCode)
                .map(clientViewAssembler::detail)
                .toList();
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static ClientTemplateVo baseViewOf(ClientTemplateDetailVo d) {
        return new ClientTemplateVo(
                d.templateId(), d.templateCode(), d.displayName(), d.status(),
                d.tags() == null ? "[]"
                        : com.innospots.nexmux.core.shared.json.NexmuxJson.write(d.tags()),
                d.validityType(), d.validityDays(), d.validTo(), d.authorizedRouteIds(),
                d.quotaSpec(), d.rateLimitSpec(), d.createdClientCount());
    }

    private Map<String, RouteVo> routesById() {
        Map<String, RouteVo> routesById = new LinkedHashMap<>();
        for (RouteVo route : routeService.listAll()) {
            routesById.put(route.routeId(), route);
        }
        return routesById;
    }

    private ClientTemplateRecord toRecord(
            ClientTemplateVo vo, ClientTemplateDetailVo detail, Map<String, RouteVo> routesById) {
        DisplayFormats formats = displayFormats.current();
        List<AuthRoute> authRoutes = (vo.authorizedRouteIds() == null ? List.<String>of() : vo.authorizedRouteIds())
                .stream()
                .map(routesById::get)
                .filter(r -> r != null)
                .map(ClientTemplateViewAssembler::authRouteOf)
                .toList();
        List<QuotaRule> quotas = PolicyDerive.quotasOf(vo.quotaSpec());
        List<RateLimitRule> rateLimits = PolicyDerive.rateLimitsOf(vo.rateLimitSpec(),
                id -> {
                    RouteVo route = routesById.get(id);
                    return route == null ? null : route.displayName();
                });
        String quotaSummary = PolicyDerive.quotaSummaryOf(quotas);
        String rateLimitSummary = PolicyDerive.rateLimitSummaryOf(rateLimits);
        String status = "ENABLED".equals(vo.status()) ? "enabled" : "disabled";
        List<String> tags = detail != null && detail.tags() != null
                ? detail.tags() : PolicyDerive.stringListOf(vo.tags());
        String validityFixedDate = vo.validTo() == null ? null : formats.date(vo.validTo());
        String validityType = vo.validityType() == null ? "never" : vo.validityType();

        return new ClientTemplateRecord(
                vo.templateId(),
                vo.templateCode(),
                vo.displayName(),
                tags,
                detail == null ? "" : nullToEmpty(detail.description()),
                status,
                STATUS_LABELS.getOrDefault(status, status),
                validityType,
                vo.validityDays(),
                validityFixedDate,
                validityLabelOf(validityType, vo.validityDays(), validityFixedDate),
                authRoutes,
                quotas,
                rateLimits,
                vo.createdClientCount(),
                detail == null ? "" : formats.dayMinuteTime(detail.createdAt()),
                detail == null ? "" : formats.dayMinuteTime(detail.updatedAt()),
                detail == null || detail.updatedBy() == null ? "—" : detail.updatedBy(),
                authRoutes.size(),
                quotaSummary,
                rateLimitSummary,
                String.join(" · ", tags),
                authRoutes.size() + " 路由 · " + quotaSummary + " · " + rateLimitSummary);
    }

    private static AuthRoute authRouteOf(RouteVo route) {
        int candidates = route.routeSpec() == null ? 0 : route.routeSpec().candidates().size();
        return new AuthRoute(
                route.routeId(),
                route.routeCode(),
                route.displayName(),
                route.alias(),
                candidates > 1 ? "多模型池" : "单模型",
                route.routeSpec() == null ? "" : route.routeSpec().ingress());
    }

    private static String validityLabelOf(String type, Integer days, String fixedDate) {
        if ("days".equals(type)) {
            return "创建后 " + days + " 天";
        }
        if ("fixed".equals(type)) {
            return "固定至 " + fixedDate;
        }
        return "永不过期";
    }

    private static ClientTemplateListResult.Counts countsOf(List<ClientTemplateRecord> universe) {
        Map<String, Long> tags = new LinkedHashMap<>();
        for (ClientTemplateRecord item : universe) {
            for (String tag : item.tags()) {
                tags.merge(tag.replace('-', '_'), 1L, Long::sum);
            }
        }
        long enabled = universe.stream().filter(t -> "enabled".equals(t.status())).count();
        return new ClientTemplateListResult.Counts(
                new EnabledTotal(enabled, universe.size()),
                new ClientTemplateListResult.StatusCounts(
                        enabled,
                        universe.stream().filter(t -> "disabled".equals(t.status())).count()),
                tags);
    }

    private static ClientTemplateListResult.Filters filtersOf(
            ClientTemplateListResult.Counts counts, List<ClientTemplateRecord> universe) {
        List<FilterChip> statusChips = List.of(
                new FilterChip("all", "全部 " + counts.all().enabled() + "/" + counts.all().total(), null),
                new FilterChip("enabled", "启用 " + counts.status().enabled(), null),
                new FilterChip("disabled", "停用 " + counts.status().disabled(), null));
        List<String> tagOrder = new ArrayList<>();
        for (ClientTemplateRecord item : universe) {
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
        return new ClientTemplateListResult.Filters(statusChips, tagChips);
    }


    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
