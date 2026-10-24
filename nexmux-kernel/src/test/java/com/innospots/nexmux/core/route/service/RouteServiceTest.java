package com.innospots.nexmux.core.route.service;

import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RouteServiceTest {

    private RouteDao routeDao;
    private ConfigAuditService configAuditService;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeDao = Mockito.mock(RouteDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        routeService = new RouteService(routeDao, configAuditService, new DependencyImpactAnalyzer());
    }

    private RouteEntity route(String id, String code, String status, String specJson) {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId(id);
        entity.setRouteCode(code);
        entity.setAlias("general");
        entity.setDisplayName("通用对话路由");
        entity.setStatus(status);
        entity.setRouteSpec(specJson);
        return entity;
    }

    @Test
    void deleteRemovesRouteAfterAudit() {
        RouteEntity entity = route("r-1", "general", "DRAFT", specJson("general", 1));
        when(routeDao.selectById("r-1")).thenReturn(entity);

        routeService.delete("r-1");

        Mockito.verify(configAuditService).record(
                Mockito.eq("ROUTE"), Mockito.eq("r-1"), Mockito.eq("DELETE"),
                any(), any());
        Mockito.verify(routeDao).deleteById(entity);
    }

    @Test
    void updateDraftRejectsDuplicateAlias() {
        // 别名唯一性：updateDraft 可改 routeSpec.alias，不得绕过 create 的唯一性校验
        RouteEntity entity = route("r-1", "code-1", "DRAFT", specJson("general", 1));
        when(routeDao.selectById("r-1")).thenReturn(entity);
        when(routeDao.selectCount(any())).thenReturn(1L);

        RouteSpec renamed = NexmuxJson.read(specJson("taken", 1), RouteSpec.class);
        assertThatThrownBy(() -> routeService.updateDraft("r-1", new RouteUpdateRequest(null, renamed)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY.fullCode()));
        Mockito.verify(routeDao, Mockito.never()).updateById(any(RouteEntity.class));
    }

    @Test
    void updateDraftSyncsAliasColumnWhenRenamed() {
        // 改名合法时同步 entity.alias 列，避免草稿规格与路由列不一致
        RouteEntity entity = route("r-1", "code-1", "DRAFT", specJson("general", 1));
        when(routeDao.selectById("r-1")).thenReturn(entity);
        when(routeDao.selectCount(any())).thenReturn(0L);

        RouteSpec renamed = NexmuxJson.read(specJson("renamed", 1), RouteSpec.class);
        routeService.updateDraft("r-1", new RouteUpdateRequest(null, renamed));

        assertThat(entity.getAlias()).isEqualTo("renamed");
        Mockito.verify(routeDao).updateById(entity);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(routeDao.selectById("r-9")).thenReturn(null);

        assertThatThrownBy(() -> routeService.delete("r-9"))
                .isInstanceOf(NexusException.class);
        Mockito.verify(routeDao, Mockito.never()).deleteById(any(RouteEntity.class));
    }

    @Test
    void enableRejectsRouteWithoutEnabledCandidates() {
        // D-01：已发布规格无启用候选时不得重新启用（Runtime 将无可选模型）
        RouteEntity entity = route("r-1", "general", "DISABLED", specJson("general", 1));
        entity.setPublishedRouteSpec("{\"alias\":\"general\",\"candidates\":[]}");
        when(routeDao.selectById("r-1")).thenReturn(entity);

        assertThatThrownBy(() -> routeService.enable("r-1"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
    }

    private static String specJson(String alias, int candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"modelCode\":\"m").append(i).append("\",\"weight\":").append(100 / Math.max(candidates, 1)).append("}");
        }
        return "{\"alias\":\"" + alias + "\",\"ingress\":\"/v1/chat/completions\","
                + "\"strategyType\":\"FIXED\",\"candidates\":[" + sb + "],"
                + "\"filterUnhealthyBindings\":true,\"schemaVersion\":1,"
                + "\"protocol\":\"OpenAI / Chat Completions\",\"tags\":[\"general\"]}";
    }

    // ── create ────────────────────────────────────────────────

    @Test
    void createWritesRouteCodeAndFullSpec() {
        when(routeDao.selectCount(any())).thenReturn(0L);

        var vo = routeService.create(new RouteCreateRequest(
                "general-chat", "general", "通用对话路由", "高频通用对话入口",
                "/v1/chat/completions", "OpenAI / Chat Completions", "FIXED",
                false, true, List.of("general"),
                List.of(new RouteCandidate("qwen3.5-flash", 100, true, "P0",
                        new BigDecimal("1.0"), new BigDecimal("1.2")))));

        assertThat(vo.routeCode()).isEqualTo("general-chat");
        Mockito.verify(routeDao).insert(Mockito.<RouteEntity>argThat(e ->
                "general-chat".equals(e.getRouteCode())
                        && e.getRouteSpec().contains("\"ingress\":\"/v1/chat/completions\"")
                        && e.getRouteSpec().contains("\"tags\":[\"general\"]")
                        && e.getRouteSpec().contains("qwen3.5-flash")));
    }

    @Test
    void createRejectsDuplicateRouteCode() {
        when(routeDao.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> routeService.create(new RouteCreateRequest(
                "general-chat", "general", "x", null, null, null, null,
                null, null, null, null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.DUPLICATE_BUSINESS_KEY.fullCode()));
    }

    // ── getByCode ─────────────────────────────────────────────

    @Test
    void getByCodeReturnsDetailWithParsedSpecs() {
        when(routeDao.selectOne(any()))
                .thenReturn(route("rt-1", "general-chat", "PUBLISHED", specJson("general", 1)));

        RouteDetailVo detail = routeService.getByCode("general-chat");

        assertThat(detail.routeCode()).isEqualTo("general-chat");
        assertThat(detail.routeSpec().ingress()).isEqualTo("/v1/chat/completions");
        assertThat(detail.routeSpec().protocol()).isEqualTo("OpenAI / Chat Completions");
        assertThat(detail.routeSpec().tags()).containsExactly("general");
        assertThat(detail.routeSpec().candidates()).hasSize(1);
    }

    @Test
    void getByCodeThrowsNotFoundWhenMissing() {
        when(routeDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> routeService.getByCode("missing"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    // ── enable / disable 状态机 ────────────────────────────────

    @Test
    void disableMovesPublishedToDisabled() {
        RouteEntity entity = route("rt-1", "general-chat", "PUBLISHED", specJson("general", 1));
        when(routeDao.selectById("rt-1")).thenReturn(entity);

        var vo = routeService.disable("rt-1");

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.status()).isEqualTo("DISABLED");
    }

    @Test
    void enableRestoresDisabledToPublished() {
        RouteEntity entity = route("rt-1", "general-chat", "DISABLED", specJson("general", 1));
        // enable 校验的是已发布规格（D-01）：DISABLED 路由必有历史发布快照
        entity.setPublishedRouteSpec(specJson("general", 1));
        when(routeDao.selectById("rt-1")).thenReturn(entity);

        var vo = routeService.enable("rt-1");

        assertThat(entity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(vo.status()).isEqualTo("PUBLISHED");
        // 与 DISABLE / PUBLISH 对称：ENABLE 也落审计
        Mockito.verify(configAuditService).record(
                Mockito.eq("ROUTE"), Mockito.eq("rt-1"), Mockito.eq("ENABLE"),
                any(), any());
    }

    @Test
    void enableRejectsDraft() {
        RouteEntity entity = route("rt-1", "general-chat", "DRAFT", specJson("general", 1));
        when(routeDao.selectById("rt-1")).thenReturn(entity);

        assertThatThrownBy(() -> routeService.enable("rt-1"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
    }

    @Test
    void disableRejectsDraft() {
        RouteEntity entity = route("rt-1", "general-chat", "DRAFT", specJson("general", 1));
        when(routeDao.selectById("rt-1")).thenReturn(entity);

        assertThatThrownBy(() -> routeService.disable("rt-1"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.OPERATION_PRECONDITION_FAILED.fullCode()));
    }

    // ── page 筛选 ─────────────────────────────────────────────

    @Test
    void pageFiltersByTypeAndTagsInMemory() {
        RouteEntity single = route("rt-1", "single-route", "PUBLISHED", specJson("a", 1));
        RouteEntity pool = route("rt-2", "pool-route", "PUBLISHED", specJson("b", 2));
        when(routeDao.selectList(any())).thenReturn(List.of(single, pool));

        var poolResult = routeService.page(new RoutePageRequest(1L, 20L, null, "pool", null, null));
        assertThat(poolResult.records()).hasSize(1);
        assertThat(poolResult.total()).isEqualTo(1);

        var tagMiss = routeService.page(new RoutePageRequest(1L, 20L, null, null, List.of("vision"), null));
        assertThat(tagMiss.records()).isEmpty();
        assertThat(tagMiss.total()).isZero();
    }

    @Test
    void pageFiltersByModelCandidate() {
        RouteEntity hit = route("rt-1", "hit-route", "PUBLISHED",
                specJsonWithModels("a", "zai/glm-5.3-highspeed"));
        RouteEntity miss = route("rt-2", "miss-route", "PUBLISHED",
                specJsonWithModels("b", "openai/gpt-5"));
        when(routeDao.selectList(any())).thenReturn(List.of(hit, miss));

        var result = routeService.page(
                new RoutePageRequest(1L, 20L, null, null, null, "zai/glm-5.3-highspeed"));
        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);

        var noMatch = routeService.page(
                new RoutePageRequest(1L, 20L, null, null, null, "anthropic/claude-4"));
        assertThat(noMatch.records()).isEmpty();
        assertThat(noMatch.total()).isZero();
    }

    private static String specJsonWithModels(String alias, String... modelCodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modelCodes.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"modelCode\":\"").append(modelCodes[i])
                    .append("\",\"weight\":").append(100 / Math.max(modelCodes.length, 1)).append("}");
        }
        return "{\"alias\":\"" + alias + "\",\"ingress\":\"/v1/chat/completions\","
                + "\"strategyType\":\"FIXED\",\"candidates\":[" + sb + "],"
                + "\"filterUnhealthyBindings\":true,\"schemaVersion\":1,"
                + "\"tags\":[\"prod\"]}";
    }
}
