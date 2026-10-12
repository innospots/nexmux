package com.innospots.nexmux.console.route;

import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RouteViewAssembler} 单测：/api/d/routes 列表与详情契约
 * （行字段与派生文案对齐 route-list / route-detail 页面绑定）。
 */
class RouteViewAssemblerTest {

    private RouteService routeService;
    private ModelService modelService;
    private RouteViewAssembler assembler;

    @BeforeEach
    void setUp() {
        routeService = Mockito.mock(RouteService.class);
        modelService = Mockito.mock(ModelService.class);
        assembler = new RouteViewAssembler(routeService, modelService, DisplayFormatsProvider.defaults());
    }

    private static ModelVo model(String code, String name) {
        return new ModelVo("m-" + code, code, name, "ENABLED", "LLM", null, null, "pv1", null, null);
    }

    private static RouteCandidate candidate(String modelCode, int weight) {
        return new RouteCandidate(modelCode, weight, null, null, null, null);
    }

    private static RouteVo route(String code, String name, String status, RouteSpec spec) {
        return new RouteVo("r-" + code, code, code, name, status, spec, null);
    }

    private static RouteSpec spec(String ingress, String protocol, String strategy,
                                  List<String> tags, RouteCandidate... candidates) {
        return new RouteSpec("alias", ingress, strategy, List.of(candidates), true, 1,
                "desc", protocol, tags, null);
    }

    private void stubRelations() {
        RouteVo single = route("chat", "Chat 路由", "PUBLISHED",
                spec("/v1/chat/completions", "OpenAI / Chat Completions", "WEIGHTED",
                        List.of("prod"), candidate("gpt-4o", 100)));
        RouteVo pool = route("pool", "池路由", "DRAFT",
                spec("/v1/responses", "OpenAI / Responses", "ROUND_ROBIN",
                        List.of("prod", "gray-release"), candidate("gpt-4o", 60), candidate("qwen", 40)));
        when(routeService.page(any(RoutePageRequest.class)))
                .thenReturn(PageResult.of(List.of(single, pool), 1, 12, 2));
        when(routeService.listAll()).thenReturn(List.of(single, pool));
        when(modelService.listAll()).thenReturn(List.of(
                model("gpt-4o", "GPT-4o"), model("qwen", "Qwen")));
    }

    @Test
    void assemblesRowShapeBoundByListPage() {
        stubRelations();

        RouteListResult result = assembler.assemble(new RoutePageRequest(1L, 12L, null, null, null, null));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);

        RouteRecord chat = result.items().get(0);
        assertThat(chat.code()).isEqualTo("chat");
        assertThat(chat.name()).isEqualTo("Chat 路由");
        assertThat(chat.type()).isEqualTo("single");
        assertThat(chat.typeLabel()).isEqualTo("单模型");
        assertThat(chat.status()).isEqualTo("enabled");
        assertThat(chat.statusLabel()).isEqualTo("启用");
        assertThat(chat.path()).isEqualTo("/v1/chat/completions");
        assertThat(chat.protocol()).isEqualTo("OpenAI / Chat Completions");
        assertThat(chat.operation()).isEqualTo("chat.completions");
        assertThat(chat.strategyLabel()).isEqualTo("加权轮询");
        assertThat(chat.strategySummary()).isEqualTo("加权轮询 (WEIGHTED)");
        assertThat(chat.entrySummary()).isEqualTo("/v1/chat/completions · OpenAI / Chat Completions");
        assertThat(chat.policySummary()).isEqualTo("1 模型 · 加权轮询");
        assertThat(chat.tags()).containsExactly("prod");
        assertThat(chat.tagsText()).isEqualTo("prod");
        assertThat(chat.enabledModelCount()).isEqualTo(1);
        assertThat(chat.weightTotal()).isEqualTo(100);

        RouteRecord pool = result.items().get(1);
        assertThat(pool.type()).isEqualTo("pool");
        assertThat(pool.typeLabel()).isEqualTo("多模型池");
        assertThat(pool.status()).isEqualTo("draft");
        assertThat(pool.statusLabel()).isEqualTo("草稿");
        assertThat(pool.operation()).isEqualTo("responses");
        assertThat(pool.strategyLabel()).isEqualTo("轮询");
        assertThat(pool.models()).hasSize(2);
        assertThat(pool.models().get(0).modelName()).isEqualTo("GPT-4o");
        assertThat(pool.models().get(1).modelCode()).isEqualTo("qwen");
        assertThat(pool.weightTotal()).isEqualTo(100);
    }

    @Test
    void computesCountsAndFiltersOverUniverse() {
        stubRelations();

        RouteListResult result = assembler.assemble(new RoutePageRequest(1L, 12L, null, null, null, null));

        assertThat(result.counts().all().enabled()).isEqualTo(1);
        assertThat(result.counts().all().total()).isEqualTo(2);
        assertThat(result.counts().status().enabled()).isEqualTo(1);
        assertThat(result.counts().status().disabled()).isZero();
        assertThat(result.counts().status().draft()).isEqualTo(1);
        assertThat(result.counts().types().get("single").total()).isEqualTo(1);
        assertThat(result.counts().types().get("pool").enabled()).isZero();
        assertThat(result.counts().tags().get("prod")).isEqualTo(2);
        // tag 计数键以 _ 替代 -（与原 BFF 口径一致）
        assertThat(result.counts().tags().get("gray_release")).isEqualTo(1);

        assertThat(result.filters().status()).containsExactly(
                new FilterChip("all", "全部 1/2", null),
                new FilterChip("enabled", "已启用 1", null),
                new FilterChip("disabled", "已停用 0", null),
                new FilterChip("draft", "草稿 1", null));
        assertThat(result.filters().type()).containsExactly(
                new FilterChip("all", "全部 1/2", null),
                new FilterChip("single", "单模型 1/1", null),
                new FilterChip("pool", "多模型池 0/1", null),
                new FilterChip("conditional", "条件路由 0/0", null));
        assertThat(result.filters().tags()).containsExactly(
                new FilterChip("all", "全部 2", null),
                new FilterChip("prod", "prod 2", null),
                new FilterChip("gray-release", "gray-release 1", null));
    }

    @Test
    void assemblesDetailWithModelEntriesAndTimestamps() {
        RouteCandidate disabled = new RouteCandidate("qwen", 40, false, "P1",
                new BigDecimal("1.2"), new BigDecimal("1.5"));
        RouteDetailVo detail = new RouteDetailVo(
                "r-chat", "chat", "chat-alias", "Chat 路由", "PUBLISHED",
                spec("/v1/chat/completions", "OpenAI / Chat Completions", "WEIGHTED",
                        List.of("prod"), candidate("gpt-4o", 60), disabled),
                null, null, "publisher",
                java.time.LocalDateTime.of(2026, 10, 1, 12, 30),
                java.time.LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(routeService.getByCode("chat")).thenReturn(detail);
        when(modelService.listAll()).thenReturn(List.of(
                model("gpt-4o", "GPT-4o"), model("qwen", "Qwen")));

        RouteRecord record = assembler.detail("chat");

        assertThat(record.code()).isEqualTo("chat");
        assertThat(record.alias()).isEqualTo("chat-alias");
        assertThat(record.description()).isEqualTo("desc");
        assertThat(record.type()).isEqualTo("pool");
        assertThat(record.models()).hasSize(2);
        assertThat(record.models().get(0).status()).isEqualTo("enabled");
        assertThat(record.models().get(1).status()).isEqualTo("disabled");
        assertThat(record.models().get(1).costRate()).isEqualByComparingTo("1.2");
        assertThat(record.models().get(1).billingRate()).isEqualByComparingTo("1.5");
        // 权重合计只计启用模型
        assertThat(record.weightTotal()).isEqualTo(60);
        assertThat(record.enabledModelCount()).isEqualTo(1);
        assertThat(record.createdAt()).isEqualTo("2026-10-01 12:30");
        assertThat(record.updatedAt()).isEqualTo("2026-10-05 08:00");
        assertThat(record.shortDate()).isEqualTo("10-05");
        assertThat(record.operator()).isEqualTo("admin");
        assertThat(record.stickySession()).isFalse();
        assertThat(record.healthFilter()).isTrue();
        // 发布快照为 null（未发布过）→ 无未发布变更
        assertThat(record.hasUnpublishedChanges()).isFalse();
    }

    @Test
    void detailFlagsUnpublishedChangesWhenDraftDiffersFromPublished() {
        RouteDetailVo detail = new RouteDetailVo(
                "r-chat", "chat", "chat-alias", "Chat 路由", "PUBLISHED",
                spec("/v1/chat/completions", "OpenAI / Chat Completions", "RANDOM",
                        List.of("prod"), candidate("gpt-4o", 100)),
                spec("/v1/chat/completions", "OpenAI / Chat Completions", "WEIGHTED",
                        List.of("prod"), candidate("gpt-4o", 100)),
                null, "publisher",
                java.time.LocalDateTime.of(2026, 10, 1, 12, 30),
                java.time.LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(routeService.getByCode("chat")).thenReturn(detail);
        when(modelService.listAll()).thenReturn(List.of(model("gpt-4o", "GPT-4o")));

        assertThat(assembler.detail("chat").hasUnpublishedChanges()).isTrue();
    }

    @Test
    void detailHasNoUnpublishedChangesWhenDraftEqualsPublished() {
        RouteSpec same = spec("/v1/chat/completions", "OpenAI / Chat Completions", "WEIGHTED",
                List.of("prod"), candidate("gpt-4o", 100));
        RouteDetailVo detail = new RouteDetailVo(
                "r-chat", "chat", "chat-alias", "Chat 路由", "PUBLISHED",
                same, same,
                null, "publisher",
                java.time.LocalDateTime.of(2026, 10, 1, 12, 30),
                java.time.LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(routeService.getByCode("chat")).thenReturn(detail);
        when(modelService.listAll()).thenReturn(List.of(model("gpt-4o", "GPT-4o")));

        assertThat(assembler.detail("chat").hasUnpublishedChanges()).isFalse();
    }

    @Test
    void fallsBackForUnknownProtocolAndMissingModel() {
        RouteVo bare = route("bare", "Bare", "DISABLED",
                spec("/v1/x", "Unknown", "FIXED", List.of(), candidate("ghost", 100)));
        when(routeService.page(any(RoutePageRequest.class)))
                .thenReturn(PageResult.of(List.of(bare), 1, 12, 1));
        when(routeService.listAll()).thenReturn(List.of(bare));
        when(modelService.listAll()).thenReturn(List.of());

        RouteListResult result = assembler.assemble(new RoutePageRequest(1L, 12L, null, null, null, null));

        RouteRecord item = result.items().get(0);
        assertThat(item.operation()).isEqualTo("—");
        assertThat(item.status()).isEqualTo("disabled");
        assertThat(item.models().get(0).modelName()).isEqualTo("ghost");
        assertThat(item.strategyLabel()).isEqualTo("固定");
    }
}
