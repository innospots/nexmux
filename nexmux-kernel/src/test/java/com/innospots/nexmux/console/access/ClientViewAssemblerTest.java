package com.innospots.nexmux.console.access;

import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.access.domain.vo.ClientDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.access.service.ClientTemplateService;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ClientViewAssembler} 单测：/api/d/clients 列表与详情契约
 * （expired 展示态派生、配额/限流整形、授权路由关联）。
 */
class ClientViewAssemblerTest {

    private ClientService clientService;
    private RouteService routeService;
    private ClientTemplateService templateService;
    private ClientViewAssembler assembler;

    @BeforeEach
    void setUp() {
        clientService = Mockito.mock(ClientService.class);
        routeService = Mockito.mock(RouteService.class);
        templateService = Mockito.mock(ClientTemplateService.class);
        assembler = new ClientViewAssembler(clientService, routeService, templateService);
    }

    private static RouteVo route(String id, String code, String name, int candidates) {
        List<RouteCandidate> list = java.util.stream.IntStream.range(0, candidates)
                .mapToObj(i -> new RouteCandidate("m" + i, 100))
                .toList();
        return new RouteVo(id, code, code, name, "PUBLISHED",
                new RouteSpec(code, "/v1/" + code, "WEIGHTED", list, true, 1), null);
    }

    private static ClientTemplateVo template(String id, String code, String name) {
        return new ClientTemplateVo(id, code, name, "ENABLED", "[]", "never", null, null,
                List.of(), null, null, 0);
    }

    private static ClientVo client(String id, String code, String name, String status,
                                   LocalDateTime expiresAt, List<String> routeIds, String templateId) {
        return new ClientVo(id, code, name, status, routeIds,
                "[{\"name\":\"q1\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\",\"limit\":1000000,\"mode\":\"hard\",\"status\":\"enabled\"}]",
                "[{\"name\":\"r1\",\"limitType\":\"RPM\",\"limit\":1000,\"scopeAll\":true,\"status\":\"enabled\"}]",
                1, "ak-" + code, "[\"prod\"]", templateId, expiresAt,
                LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    private void stubRelations() {
        when(routeService.listAll()).thenReturn(List.of(
                route("r1", "general", "通用", 1),
                route("r2", "pool", "池", 2)));
        when(templateService.listAll()).thenReturn(List.of(
                template("t1", "team-default", "团队默认")));
    }

    private void stubClients() {
        ClientVo active = client("c1", "app-web", "Web 应用", "ENABLED", null,
                List.of("r1", "r2"), "t1");
        ClientVo disabled = client("c2", "app-old", "旧应用", "DISABLED", null, List.of(), null);
        ClientVo expired = client("c3", "app-exp", "过期应用", "ENABLED",
                LocalDateTime.now().minusDays(1), List.of(), null);
        when(clientService.page(any())).thenReturn(PageResult.of(List.of(active), 1, 10, 1));
        when(clientService.listAll()).thenReturn(List.of(active, disabled, expired));
    }

    @Test
    void assemblesRowShapeBoundByListAndDetailPages() {
        stubRelations();
        stubClients();

        ClientListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientPageRequest(
                        1L, 10L, null, null, null, null),
                "all");

        assertThat(result.items()).hasSize(1);
        ClientRecord web = result.items().get(0);
        assertThat(web.code()).isEqualTo("app-web");
        assertThat(web.status()).isEqualTo("enabled");
        assertThat(web.statusLabel()).isEqualTo("启用");
        assertThat(web.routeCount()).isEqualTo(2);
        assertThat(web.authRoutes()).hasSize(2);
        assertThat(web.authRoutes().get(0).typeLabel()).isEqualTo("单模型");
        assertThat(web.authRoutes().get(1).typeLabel()).isEqualTo("多模型池");
        assertThat(web.templateCode()).isEqualTo("team-default");
        assertThat(web.templateName()).isEqualTo("团队默认");
        assertThat(web.appKeySummary()).isEqualTo("ak-app-web · 永不过期 · 2 路由 · 1000 RPM");
        assertThat(web.tagsText()).isEqualTo("prod");
        assertThat(web.secretLastRotatedAt()).isEqualTo("2026-09-01 10:00");

        assertThat(web.quotas()).hasSize(1);
        assertThat(web.quotas().get(0).quotaTypeLabel()).isEqualTo("总配额");
        assertThat(web.quotas().get(0).metricLabel()).isEqualTo("Request");
        assertThat(web.quotas().get(0).limitText()).isEqualTo("1,000,000");
        assertThat(web.quotas().get(0).modeLabel()).isEqualTo("Hard Limit");
        assertThat(web.quotas().get(0).usageUsed()).isNull();
        assertThat(web.quotaSummary()).isEqualTo("总");
        assertThat(web.rateLimits().get(0).limitTypeLabel()).isEqualTo("RPM（每分钟请求数）");
        assertThat(web.rateLimits().get(0).scopeLabel()).isEqualTo("全部授权路由");
        assertThat(web.rateLimitSummary()).isEqualTo("1000 RPM");
    }

    @Test
    void derivesExpiredAsDisplayStatus() {
        stubRelations();
        stubClients();

        ClientListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientPageRequest(
                        1L, 10L, null, null, null, null),
                "all");

        // 构成口径：enabled 1（app-web）、disabled 1、expired 1（ENABLED 但已过期）
        assertThat(result.counts().all().enabled()).isEqualTo(1);
        assertThat(result.counts().all().total()).isEqualTo(3);
        assertThat(result.counts().status().enabled()).isEqualTo(1);
        assertThat(result.counts().status().disabled()).isEqualTo(1);
        assertThat(result.counts().status().expired()).isEqualTo(1);
        assertThat(result.filters().tags()).containsExactly(
                new FilterChip("all", "全部 3", null),
                new FilterChip("prod", "prod 3", null));
    }

    @Test
    void filtersExpiredLocallyWhenChipSelected() {
        stubRelations();
        ClientVo active = client("c1", "app-web", "Web 应用", "ENABLED", null, List.of(), null);
        ClientVo expired = client("c3", "app-exp", "过期应用", "ENABLED",
                LocalDateTime.now().minusDays(1), List.of(), null);
        // expired 快筛：后端拉 ENABLED 全量（pageSize 5000），装配层过滤 + 切片
        when(clientService.page(any())).thenAnswer(inv -> {
            com.innospots.nexmux.core.access.domain.request.ClientPageRequest req = inv.getArgument(0);
            assertThat(req.status()).isEqualTo("ENABLED");
            assertThat(req.pageSize()).isEqualTo(5000L);
            return PageResult.of(List.of(active, expired), 1, 5000, 2);
        });
        when(clientService.listAll()).thenReturn(List.of(active, expired));

        ClientListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientPageRequest(
                        1L, 10L, "ENABLED", null, null, null),
                "expired");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).code()).isEqualTo("app-exp");
        assertThat(result.items().get(0).status()).isEqualTo("expired");
        assertThat(result.items().get(0).statusLabel()).isEqualTo("已过期");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void assemblesDetailWithExpirySummary() {
        stubRelations();
        ClientDetailVo detail = new ClientDetailVo(
                "c1", "app-web", "Web 应用", "ENABLED", "ak-app-web", 2,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                List.of("r1"),
                "[{\"name\":\"q1\",\"quotaType\":\"cyclic\",\"metric\":\"COST\",\"limit\":500,"
                        + "\"mode\":\"soft\",\"cycleUnit\":\"month\",\"status\":\"enabled\"}]",
                "[]", "t1", List.of("prod", "team"),
                LocalDateTime.of(2027, 1, 31, 0, 0),
                LocalDateTime.of(2026, 10, 1, 12, 30),
                LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(clientService.getByCode("app-web")).thenReturn(detail);

        ClientRecord record = assembler.detail("app-web");

        assertThat(record.expiresAt()).isEqualTo("2027-01-31 00:00");
        assertThat(record.appKeySummary()).isEqualTo("ak-app-web · 到期 2027-01-31 · 1 路由 · —");
        assertThat(record.quotaSummary()).isEqualTo("月循环");
        assertThat(record.quotas().get(0).cycleLabel()).isEqualTo("每月（自然月）");
        assertThat(record.tags()).containsExactly("prod", "team");
        assertThat(record.createdAt()).isEqualTo("2026-10-01 12:30");
        assertThat(record.operator()).isEqualTo("admin");
    }
}
