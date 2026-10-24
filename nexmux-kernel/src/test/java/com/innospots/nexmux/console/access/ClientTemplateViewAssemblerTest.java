package com.innospots.nexmux.console.access;

import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
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
 * {@link ClientTemplateViewAssembler} 单测：/api/d/client-templates 列表与详情契约
 * （有效期文案、配额/限流整形、授权路由关联、created-clients）。
 */
class ClientTemplateViewAssemblerTest {

    private ClientTemplateService templateService;
    private RouteService routeService;
    private ClientViewAssembler clientViewAssembler;
    private ClientTemplateViewAssembler assembler;

    @BeforeEach
    void setUp() {
        templateService = Mockito.mock(ClientTemplateService.class);
        routeService = Mockito.mock(RouteService.class);
        clientViewAssembler = Mockito.mock(ClientViewAssembler.class);
        assembler = new ClientTemplateViewAssembler(templateService, routeService, clientViewAssembler,
                DisplayFormatsProvider.defaults());
    }

    private static RouteVo route(String id, String code, String name) {
        return new RouteVo(id, code, code, name, "PUBLISHED",
                new RouteSpec(code, "/v1/" + code, "WEIGHTED",
                        List.of(new RouteCandidate("m1", 100)), true, 1), null);
    }

    private static ClientTemplateVo template(String id, String code, String name, String status,
                                             String validityType, Integer validityDays,
                                             LocalDateTime validTo, String tags) {
        return new ClientTemplateVo(id, code, name, status, tags, validityType, validityDays, validTo,
                List.of("r1"),
                "[{\"name\":\"q1\",\"quotaType\":\"cyclic\",\"metric\":\"COST\",\"limit\":500,"
                        + "\"mode\":\"soft\",\"cycleUnit\":\"month\",\"status\":\"enabled\"}]",
                "[{\"name\":\"r1\",\"limitType\":\"CONCURRENT\",\"limit\":20,\"scopeAll\":true,\"status\":\"enabled\"}]",
                3);
    }

    private void stubRelations() {
        when(routeService.listAll()).thenReturn(List.of(route("r1", "general", "通用")));
        ClientTemplateVo days = template("t1", "team-default", "团队默认", "ENABLED",
                "days", 365, null, "[\"team\"]");
        ClientTemplateVo fixed = template("t2", "fixed-date", "固定日期", "DISABLED",
                "fixed", null, LocalDateTime.of(2027, 12, 31, 0, 0), "[]");
        when(templateService.page(any())).thenReturn(PageResult.of(List.of(days, fixed), 1, 12, 2));
        when(templateService.listAll()).thenReturn(List.of(days, fixed));
    }

    @Test
    void assemblesRowShapeBoundByListPage() {
        stubRelations();

        ClientTemplateListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest(
                        1L, 12L, null, null, null));

        assertThat(result.total()).isEqualTo(2);
        ClientTemplateRecord days = result.items().get(0);
        assertThat(days.code()).isEqualTo("team-default");
        assertThat(days.status()).isEqualTo("enabled");
        assertThat(days.statusLabel()).isEqualTo("启用");
        assertThat(days.validityType()).isEqualTo("days");
        assertThat(days.validityDays()).isEqualTo(365);
        assertThat(days.validityLabel()).isEqualTo("创建后 365 天");
        assertThat(days.routeCount()).isEqualTo(1);
        assertThat(days.authRoutes().get(0).name()).isEqualTo("通用");
        assertThat(days.createdClientCount()).isEqualTo(3);
        assertThat(days.quotaSummary()).isEqualTo("月循环");
        assertThat(days.rateLimitSummary()).isEqualTo("20 并发");
        assertThat(days.policySummary()).isEqualTo("1 路由 · 月循环 · 20 并发");
        assertThat(days.tags()).containsExactly("team");
        assertThat(days.tagsText()).isEqualTo("team");

        ClientTemplateRecord fixed = result.items().get(1);
        assertThat(fixed.status()).isEqualTo("disabled");
        assertThat(fixed.validityLabel()).isEqualTo("固定至 2027-12-31");
        assertThat(fixed.validityFixedDate()).isEqualTo("2027-12-31");
    }

    @Test
    void computesCountsAndFiltersOverUniverse() {
        stubRelations();

        ClientTemplateListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest(
                        1L, 12L, null, null, null));

        assertThat(result.counts().status().enabled()).isEqualTo(1);
        assertThat(result.counts().status().disabled()).isEqualTo(1);
        assertThat(result.counts().tags().get("team")).isEqualTo(1);
        assertThat(result.filters().status()).containsExactly(
                new FilterChip("all", "全部 1/2", null),
                new FilterChip("enabled", "启用 1", null),
                new FilterChip("disabled", "停用 1", null));
        assertThat(result.filters().tags()).containsExactly(
                new FilterChip("all", "全部 2", null),
                new FilterChip("team", "team 1", null));
    }

    @Test
    void defaultsNeverValidity() {
        when(routeService.listAll()).thenReturn(List.of());
        ClientTemplateVo never = template("t9", "never-exp", "永不过期", "ENABLED",
                "never", null, null, "[]");
        when(templateService.page(any())).thenReturn(PageResult.of(List.of(never), 1, 12, 1));
        when(templateService.listAll()).thenReturn(List.of(never));

        ClientTemplateListResult result = assembler.assemble(
                new com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest(
                        1L, 12L, null, null, null));

        assertThat(result.items().get(0).validityLabel()).isEqualTo("永不过期");
        assertThat(result.items().get(0).validityFixedDate()).isNull();
    }

    @Test
    void assemblesDetailWithTimestamps() {
        when(routeService.listAll()).thenReturn(List.of(route("r1", "general", "通用")));
        ClientTemplateDetailVo detail = new ClientTemplateDetailVo(
                "t1", "team-default", "团队默认", "ENABLED", "desc", List.of("team"),
                "days", 365, null, List.of("r1"), "[]", "[]", 3,
                LocalDateTime.of(2026, 10, 1, 12, 30),
                LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(templateService.getByCode("team-default")).thenReturn(detail);

        ClientTemplateRecord record = assembler.detail("team-default");

        assertThat(record.description()).isEqualTo("desc");
        assertThat(record.createdAt()).isEqualTo("2026-10-01 12:30");
        assertThat(record.updatedAt()).isEqualTo("2026-10-05 08:00");
        assertThat(record.operator()).isEqualTo("admin");
    }

    @Test
    void resolvesCreatedClientsViaTemplateId() {
        ClientTemplateDetailVo detail = new ClientTemplateDetailVo(
                "t1", "team-default", "团队默认", "ENABLED", "", List.of(),
                "days", 365, null, List.of(), "[]", "[]", 1,
                null, null, null);
        when(templateService.getByCode("team-default")).thenReturn(detail);
        ClientVo created = new ClientVo("c1", "app-web", "Web 应用", "ENABLED", List.of(),
                "[]", "[]", 1, "ak-1", "[]", "t1", null, null);
        when(templateService.createdClients("t1")).thenReturn(List.of(created));
        ClientRecord record = Mockito.mock(ClientRecord.class);
        when(clientViewAssembler.detail("app-web")).thenReturn(record);

        assertThat(assembler.createdClients("team-default")).containsExactly(record);
    }
}
