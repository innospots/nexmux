package com.innospots.nexmux.console.provider;

import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ProviderViewAssembler} 单测：/api/d/providers 列表契约
 * （{items,total,filters}，行字段与派生文案对齐 provider-list.yaml 绑定）。
 */
class ProviderViewAssemblerTest {

    private ProviderService providerService;
    private ModelService modelService;
    private RouteService routeService;
    private ProviderViewAssembler assembler;

    @BeforeEach
    void setUp() {
        providerService = Mockito.mock(ProviderService.class);
        modelService = Mockito.mock(ModelService.class);
        routeService = Mockito.mock(RouteService.class);
        assembler = new ProviderViewAssembler(providerService, modelService, routeService);
    }

    private static ProviderVo provider(String id, String code, String name, String vendor,
                                       String status, String keyStatus, long modelCount) {
        return new ProviderVo(id, code, name, "COMMERCIAL", "openai-completions", vendor, vendor,
                null, "https://api.example.com", null, 60000, status, keyStatus, modelCount);
    }

    private static ModelVo model(String id, String code, String status, String providerId) {
        return new ModelVo(id, code, code, status, "LLM", null, null, providerId, null, null);
    }

    private static RouteVo route(String code, String status, String... modelCodes) {
        List<RouteCandidate> candidates = java.util.Arrays.stream(modelCodes)
                .map(c -> new RouteCandidate(c, 100))
                .toList();
        return new RouteVo("r-" + code, code, code, code, status,
                new RouteSpec(code, "/v1", "WEIGHTED", candidates, false, 1), null);
    }

    private void stubRelations() {
        ProviderVo zai = provider("pv1", "zai", "Z.AI", "zai", "ENABLED", "none", 2);
        ProviderVo openai = provider("pv2", "openai-main", "OpenAI", "openai", "DISABLED", "configured", 1);
        when(providerService.page(any(ProviderPageRequest.class)))
                .thenReturn(PageResult.of(List.of(zai, openai), 1, 8, 2));
        when(providerService.listAll()).thenReturn(List.of(zai, openai));
        when(modelService.listAll()).thenReturn(List.of(
                model("m1", "gpt-4o", "ENABLED", "pv2"),
                model("m2", "z-ai-1", "ENABLED", "pv1"),
                model("m3", "z-ai-2", "DISABLED", "pv1")));
        when(routeService.listAll()).thenReturn(List.of(
                route("r1", "PUBLISHED", "gpt-4o", "z-ai-1"),
                route("r2", "DRAFT", "z-ai-1")));
    }

    @Test
    void assemblesRowShapeBoundByListPage() {
        stubRelations();

        ProviderListResult result = assembler.assemble(new ProviderPageRequest(1L, 8L, null, null, null, null));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);

        ProviderRecord zai = result.items().get(0);
        assertThat(zai.code()).isEqualTo("zai");
        assertThat(zai.displayName()).isEqualTo("Z.AI");
        assertThat(zai.icon()).isEqualTo("zap");
        assertThat(zai.family()).isEqualTo("zai");
        assertThat(zai.status()).isEqualTo("enabled");
        assertThat(zai.keyStatus()).isEqualTo("none");
        assertThat(zai.maskedKey()).isEqualTo("—");
        assertThat(zai.capabilities()).isEmpty();
        assertThat(zai.modelCount()).isEqualTo(2);
        assertThat(zai.enabledModelCount()).isEqualTo(1);
        assertThat(zai.routes().total()).isEqualTo(2);
        assertThat(zai.routes().enabled()).isEqualTo(1);
        assertThat(zai.routeSummary()).isEqualTo("2 条路由 · 1 条启用");

        ProviderRecord openai = result.items().get(1);
        assertThat(openai.status()).isEqualTo("disabled");
        assertThat(openai.keyStatus()).isEqualTo("configured");
        assertThat(openai.maskedKey()).isEqualTo("••••••••");
        assertThat(openai.routeSummary()).isEqualTo("1 条路由 · 1 条启用");
        assertThat(openai.enabledModelCount()).isEqualTo(1);
    }

    @Test
    void computesCountsOverUnfilteredUniverse() {
        stubRelations();

        ProviderListResult result = assembler.assemble(new ProviderPageRequest(1L, 8L, null, null, null, null));

        assertThat(result.counts().all().enabled()).isEqualTo(1);
        assertThat(result.counts().all().total()).isEqualTo(2);
        assertThat(result.counts().status().enabled()).isEqualTo(1);
        assertThat(result.counts().status().disabled()).isEqualTo(1);
        assertThat(result.counts().status().pending()).isZero();
        assertThat(result.counts().status().none()).isEqualTo(1);
        assertThat(result.counts().types().get("zai").total()).isEqualTo(1);
        assertThat(result.counts().types().get("openai").enabled()).isZero();
    }

    @Test
    void buildsFilterChipsWithMockAlignedTexts() {
        stubRelations();

        ProviderListResult result = assembler.assemble(new ProviderPageRequest(1L, 8L, null, null, null, null));

        assertThat(result.filters().status()).containsExactly(
                new FilterChip("all", "全部 1/2", null),
                new FilterChip("enabled", "已启用 1", "#22c55e"),
                new FilterChip("disabled", "已停用 1", "#9ca3af"),
                new FilterChip("pending", "待填密钥 0", "#f59e0b"),
                new FilterChip("none", "无密钥 1", "#ef4444"));
        assertThat(result.filters().types()).containsExactly(
                new FilterChip("zai", "Z.AI 1/1", null),
                new FilterChip("openai", "OpenAI 0/1", "#3b82f6"));
    }

    @Test
    void marksProviderWithoutRoutes() {
        when(providerService.page(any(ProviderPageRequest.class)))
                .thenReturn(PageResult.of(List.of(provider("pv9", "solo", "Solo", "misc", "ENABLED", "pending", 0)), 1, 8, 1));
        when(providerService.listAll())
                .thenReturn(List.of(provider("pv9", "solo", "Solo", "misc", "ENABLED", "pending", 0)));
        when(modelService.listAll()).thenReturn(List.of());
        when(routeService.listAll()).thenReturn(List.of());

        ProviderListResult result = assembler.assemble(new ProviderPageRequest(1L, 8L, null, null, null, null));

        ProviderRecord solo = result.items().get(0);
        assertThat(solo.routeSummary()).isEqualTo("未关联路由");
        assertThat(solo.routes().total()).isZero();
        assertThat(solo.maskedKey()).isEqualTo("待配置");
        assertThat(result.filters().status().get(3).text()).isEqualTo("待填密钥 1");
    }

    @Test
    void assemblesDetailWithKeyVersionAndModelRows() {
        ProviderDetailVo detail = new ProviderDetailVo(
                "pv1", "zai", "Z.AI", "COMMERCIAL", "openai-completions", "zai", "zai",
                null, "https://api.z.ai", "cn", 60000, "http://proxy", "desc",
                "{\"chat\":true,\"tools\":true}", "ENABLED", "configured", 3, null,
                java.util.List.of(
                        new com.innospots.nexmux.core.provider.domain.vo.ProviderModelRowVo(
                                "glm-5", "GLM-5", "LLM", "glm-5-up", "ENABLED"),
                        new com.innospots.nexmux.core.provider.domain.vo.ProviderModelRowVo(
                                "glm-4", "GLM-4", "LLM", "glm-4-up", "DISABLED")),
                java.time.LocalDateTime.of(2026, 10, 1, 12, 30),
                java.time.LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(providerService.getByCode("zai")).thenReturn(detail);
        when(modelService.listAll()).thenReturn(java.util.List.of());
        when(routeService.listAll()).thenReturn(java.util.List.of(
                route("r1", "PUBLISHED", "glm-5")));

        ProviderRecord record = assembler.detail("zai");

        assertThat(record.code()).isEqualTo("zai");
        assertThat(record.proxy()).isEqualTo("http://proxy");
        assertThat(record.description()).isEqualTo("desc");
        assertThat(record.maskedKey()).isEqualTo("•••••••• (v3)");
        assertThat(record.capabilities()).containsExactly("chat", "tools");
        assertThat(record.models()).hasSize(2);
        assertThat(record.models().get(0).status()).isEqualTo("ONLINE");
        assertThat(record.models().get(0).routes()).isEqualTo(1);
        assertThat(record.models().get(1).status()).isEqualTo("OFFLINE");
        assertThat(record.modelCount()).isEqualTo(2);
        assertThat(record.enabledModelCount()).isEqualTo(1);
        assertThat(record.createdAt()).isEqualTo("2026-10-01 12:30");
        assertThat(record.updatedAt()).isEqualTo("2026-10-05 08:00");
        assertThat(record.operator()).isEqualTo("admin");
    }

    @Test
    void buildsFormOptionsFromAllProviders() {
        when(providerService.listAll()).thenReturn(java.util.List.of(
                provider("pv1", "zai", "Z.AI", "zai", "ENABLED", "none", 0),
                provider("pv2", "openai-main", "OpenAI", "openai", "DISABLED", "configured", 0)));

        assertThat(assembler.options()).containsExactly(
                new ProviderOption("Z.AI", "zai"),
                new ProviderOption("OpenAI", "openai-main"));
    }
}
