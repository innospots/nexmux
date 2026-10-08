package com.innospots.nexmux.console.access;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexus.base.domain.response.PageResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Client UI 投影（/api/d/clients 契约的装配点）：核心域 VO → 展示记录，
 * expired 展示态派生 / 授权路由关联 / 配额限流整形 / counts / filters 全部在此完成。
 * 构成口径基于全量（P0 数据量小），不随当前筛选变化。
 */
@RequiredArgsConstructor
public class ClientViewAssembler {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "enabled", "启用",
            "disabled", "停用",
            "expired", "已过期");

    /** enabled/expired 快筛拉全量 ENABLED 的自保护上限（与原 BFF 一致）。 */
    private static final long LOCAL_FILTER_PAGE_SIZE = 5000L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ClientService clientService;
    private final RouteService routeService;
    private final ClientTemplateService clientTemplateService;

    /**
     * 分页列表：items 为当前页，counts/filters 为全量构成口径。
     *
     * @param request  后端查询请求（status 已由端点映射为 ENABLED / DISABLED / null）
     * @param uiStatus 页面快筛原值（all / enabled / disabled / expired）；
     *                 enabled/expired 为展示态，需拉全量 ENABLED 本地过滤 + 切片
     */
    public ClientListResult assemble(ClientPageRequest request, String uiStatus) {
        boolean localFilter = "enabled".equals(uiStatus) || "expired".equals(uiStatus);
        ClientPageRequest effective = localFilter
                ? new ClientPageRequest(1L, LOCAL_FILTER_PAGE_SIZE, request.status(),
                        request.name(), request.code(), request.tags())
                : request;
        PageResult<ClientVo> page = clientService.page(effective);
        Relations relations = relations();

        List<ClientRecord> records = page.records().stream()
                .map(vo -> toRecord(vo, null, relations))
                .toList();
        long total = page.total();
        long pages = page.pages();
        if (localFilter) {
            records = records.stream().filter(c -> c.status().equals(uiStatus)).toList();
            total = records.size();
            pages = (long) Math.ceil((double) total / request.pageSize());
            long from = Math.min((request.pageNo() - 1) * request.pageSize(), records.size());
            long to = Math.min(from + request.pageSize(), records.size());
            records = records.subList((int) from, (int) to);
        }

        List<ClientRecord> universe = clientService.listAll().stream()
                .map(vo -> toRecord(vo, null, relations))
                .toList();
        ClientListResult.Counts counts = countsOf(universe);
        return new ClientListResult(
                records, total, request.pageNo(), request.pageSize(), pages,
                counts, filtersOf(counts, universe));
    }

    /**
     * 按稳定业务键查询详情。
     */
    public ClientRecord detail(String clientCode) {
        ClientDetailVo detail = clientService.getByCode(clientCode);
        return toRecord(baseViewOf(detail), detail, relations());
    }

    /** 详情 VO → 概要视图字段（record 无继承，显式提取公共字段）。 */
    private static ClientVo baseViewOf(ClientDetailVo d) {
        return new ClientVo(
                d.clientId(), d.clientCode(), d.displayName(), d.status(), d.authorizedRouteIds(),
                d.quotaSpec(), d.rateLimitSpec(), d.appKeyVersion(), d.appKeyId(),
                d.tags() == null ? "[]" : NexmuxJson.write(d.tags()),
                d.sourceTemplateId(), d.expiresAt(), d.appKeyRotatedAt());
    }

    private Relations relations() {
        Map<String, RouteVo> routesById = new LinkedHashMap<>();
        for (RouteVo route : routeService.listAll()) {
            routesById.put(route.routeId(), route);
        }
        Map<String, ClientTemplateVo> templatesById = new LinkedHashMap<>();
        for (ClientTemplateVo template : clientTemplateService.listAll()) {
            templatesById.put(template.templateId(), template);
        }
        return new Relations(routesById, templatesById);
    }

    private record Relations(
            Map<String, RouteVo> routesById,
            Map<String, ClientTemplateVo> templatesById) {
    }

    private static ClientRecord toRecord(ClientVo vo, ClientDetailVo detail, Relations relations) {
        List<AuthRoute> authRoutes = (vo.authorizedRouteIds() == null ? List.<String>of() : vo.authorizedRouteIds())
                .stream()
                .map(relations.routesById::get)
                .filter(r -> r != null)
                .map(ClientViewAssembler::authRouteOf)
                .toList();
        List<QuotaRule> quotas = PolicyDerive.quotasOf(vo.quotaSpec());
        List<RateLimitRule> rateLimits = PolicyDerive.rateLimitsOf(vo.rateLimitSpec(),
                id -> {
                    RouteVo route = relations.routesById.get(id);
                    return route == null ? null : route.displayName();
                });

        String status = statusOf(vo.status(), vo.expiresAt());
        String rateLimitSummary = PolicyDerive.rateLimitSummaryOf(rateLimits);
        String expiresAt = timeText(vo.expiresAt());
        ClientTemplateVo template = relations.templatesById.get(
                vo.sourceTemplateId() == null ? "" : vo.sourceTemplateId());
        List<String> tags = detail != null && detail.tags() != null
                ? detail.tags() : PolicyDerive.stringListOf(vo.tags());
        String appKeyId = vo.appKeyId() == null ? "" : vo.appKeyId();

        return new ClientRecord(
                vo.clientCode(),
                vo.displayName(),
                tags,
                "",
                status,
                STATUS_LABELS.getOrDefault(status, status),
                expiresAt,
                appKeyId,
                timeText(vo.appKeyRotatedAt()),
                template == null ? null : template.templateCode(),
                template == null ? null : template.displayName(),
                authRoutes,
                quotas,
                rateLimits,
                detail == null ? "" : timeText(detail.createdAt()),
                detail == null ? "" : timeText(detail.updatedAt()),
                detail == null || detail.updatedBy() == null ? "—" : detail.updatedBy(),
                authRoutes.size(),
                PolicyDerive.quotaSummaryOf(quotas),
                rateLimitSummary,
                String.join(" · ", tags),
                expiresAt.isEmpty()
                        ? appKeyId + " · 永不过期 · " + authRoutes.size() + " 路由 · " + rateLimitSummary
                        : appKeyId + " · 到期 " + expiresAt.substring(0, 10)
                                + " · " + authRoutes.size() + " 路由 · " + rateLimitSummary);
    }

    private static AuthRoute authRouteOf(RouteVo route) {
        int candidates = route.routeSpec() == null ? 0 : route.routeSpec().candidates().size();
        return new AuthRoute(
                route.routeCode(),
                route.displayName(),
                route.alias(),
                candidates > 1 ? "多模型池" : "单模型",
                route.routeSpec() == null ? "" : route.routeSpec().ingress());
    }

    /** 展示状态：ENABLED 且已过期 → expired（展示层语义，后端无定时任务）。 */
    private static String statusOf(String entityStatus, LocalDateTime expiresAt) {
        if ("ENABLED".equals(entityStatus)) {
            if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
                return "expired";
            }
            return "enabled";
        }
        return "disabled";
    }

    private static ClientListResult.Counts countsOf(List<ClientRecord> universe) {
        Map<String, Long> tags = new LinkedHashMap<>();
        for (ClientRecord item : universe) {
            for (String tag : item.tags()) {
                tags.merge(tag.replace('-', '_'), 1L, Long::sum);
            }
        }
        long enabled = universe.stream().filter(c -> "enabled".equals(c.status())).count();
        return new ClientListResult.Counts(
                new EnabledTotal(enabled, universe.size()),
                new ClientListResult.StatusCounts(
                        enabled,
                        universe.stream().filter(c -> "disabled".equals(c.status())).count(),
                        universe.stream().filter(c -> "expired".equals(c.status())).count()),
                tags);
    }

    private static ClientListResult.Filters filtersOf(
            ClientListResult.Counts counts, List<ClientRecord> universe) {
        List<String> tagOrder = new ArrayList<>();
        for (ClientRecord item : universe) {
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
        return new ClientListResult.Filters(tagChips);
    }

    private static String timeText(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }
}
