package com.innospots.nexmux.console.model;

import com.innospots.nexmux.console.shared.FilterChip;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.model.service.ModelService;
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
 * {@link ModelViewAssembler} 单测：/api/d/models 列表契约
 * （{items,total,filters}，行字段与派生文案对齐 model-list.yaml 绑定）。
 */
class ModelViewAssemblerTest {

    private ModelService modelService;
    private ProviderService providerService;
    private RouteService routeService;
    private ModelViewAssembler assembler;

    @BeforeEach
    void setUp() {
        modelService = Mockito.mock(ModelService.class);
        providerService = Mockito.mock(ProviderService.class);
        routeService = Mockito.mock(RouteService.class);
        assembler = new ModelViewAssembler(modelService, providerService, routeService, DisplayFormatsProvider.defaults());
    }

    private static ProviderVo openai() {
        return new ProviderVo("pv2", "openai-main", "OpenAI", "COMMERCIAL", "openai-completions",
                "openai", "openai", null, "https://api.openai.com", null, 60000,
                "ENABLED", "configured", 0);
    }

    private void stubRelations() {
        ModelVo chat = new ModelVo("m1", "openai/o4-mini", "o4 mini", "ENABLED", "LLM",
                128000L, 4096L, "pv2",
                "{\"unit\":\"per_million_tokens\",\"input\":1.1,\"output\":4.4}", null);
        ModelVo embedding = new ModelVo("m2", "text-embedding", "TE", "DISABLED", "Embedding",
                8192L, 0L, "pv2",
                "{\"tiers\":[{\"input\":0.13,\"output\":0},{\"input\":0.1,\"output\":0}]}",
                "[{\"providerId\":\"pv3\"}]");
        when(modelService.page(any(ModelPageRequest.class)))
                .thenReturn(PageResult.of(List.of(chat, embedding), 1, 11, 2));
        when(modelService.listAll()).thenReturn(List.of(chat, embedding));
        when(providerService.listAll()).thenReturn(List.of(openai()));
        when(routeService.listAll()).thenReturn(List.of(
                route("r1", "PUBLISHED", "openai/o4-mini"),
                route("r2", "DRAFT", "openai/o4-mini", "text-embedding")));
    }

    private static RouteVo route(String code, String status, String... modelCodes) {
        List<RouteCandidate> candidates = java.util.Arrays.stream(modelCodes)
                .map(c -> new RouteCandidate(c, 100))
                .toList();
        return new RouteVo("r-" + code, code, code, code, status,
                new RouteSpec(code, "/v1", "WEIGHTED", candidates, false, 1), null);
    }

    @Test
    void assemblesRowShapeBoundByListPage() {
        stubRelations();

        ModelListResult result = assembler.assemble(new ModelPageRequest(1L, 11L, null, null, null));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);

        ModelRecord chat = result.items().get(0);
        // modelCode 中的 / 以 ~ 暴露（页面路径段不支持 /）
        assertThat(chat.code()).isEqualTo("openai~o4-mini");
        assertThat(chat.name()).isEqualTo("o4 mini");
        assertThat(chat.type()).isEqualTo("LLM");
        assertThat(chat.typeLabel()).isEqualTo("大语言模型 (LLM)");
        assertThat(chat.providerIcon()).isEqualTo("zap");
        assertThat(chat.pricingSummary()).isEqualTo("$1.1 / $4.4 /M");
        assertThat(chat.tierLabel()).isEmpty();
        assertThat(chat.specSummary()).isEqualTo("128K · 4.1K");
        assertThat(chat.status()).isEqualTo("enabled");
        assertThat(chat.routes().total()).isEqualTo(2);
        assertThat(chat.routes().active()).isEqualTo(1);
        assertThat(chat.routeSummary()).isEqualTo("2 条路由 · 1 条活跃");
        assertThat(chat.suppliers()).hasSize(1);

        ModelRecord embedding = result.items().get(1);
        assertThat(embedding.pricingSummary()).isEqualTo("$0.13 / $— /M");
        assertThat(embedding.tierLabel()).isEqualTo("2档");
        assertThat(embedding.specSummary()).isEqualTo("8.2K · —");
        assertThat(embedding.status()).isEqualTo("disabled");
        assertThat(embedding.routeSummary()).isEqualTo("1 条路由 · 0 条活跃");
        // 主绑定 + 1 条扩展绑定
        assertThat(embedding.suppliers()).hasSize(2);
    }

    @Test
    void marksModelWithoutPricingAndRoutes() {
        ModelVo bare = new ModelVo("m9", "bare", "Bare", "ENABLED", "Image",
                null, null, null, null, null);
        when(modelService.page(any(ModelPageRequest.class)))
                .thenReturn(PageResult.of(List.of(bare), 1, 11, 1));
        when(modelService.listAll()).thenReturn(List.of(bare));
        when(providerService.listAll()).thenReturn(List.of(openai()));
        when(routeService.listAll()).thenReturn(List.of());

        ModelListResult result = assembler.assemble(new ModelPageRequest(1L, 11L, null, null, null));

        ModelRecord item = result.items().get(0);
        assertThat(item.pricingSummary()).isEqualTo("未定价");
        assertThat(item.routeSummary()).isEqualTo("未关联路由");
        assertThat(item.specSummary()).isEqualTo("0 · —");
        assertThat(item.providerIcon()).isEqualTo("zap");
        assertThat(item.suppliers()).isEmpty();
    }

    @Test
    void buildsTypeAndProviderFilterChips() {
        stubRelations();

        ModelListResult result = assembler.assemble(new ModelPageRequest(1L, 11L, null, null, null));

        assertThat(result.filters().type()).containsExactly(
                new FilterChip("all", "全部 2", null),
                new FilterChip("LLM", "大语言模型 1", null),
                new FilterChip("Image", "文生图 0", null),
                new FilterChip("Audio", "音频 0", null),
                new FilterChip("Embedding", "Embedding 1", null));
        assertThat(result.filters().providers()).containsExactly(
                new FilterChip("all", "全部 2", null),
                new FilterChip("openai-main", "OpenAI 2", null));
    }

    @Test
    void assemblesDetailWithSuppliersParamsAndTexts() {
        ModelDetailVo detail = new ModelDetailVo(
                "m1", "openai/o4-mini", "o4 mini", "LLM", "ENABLED", null,
                java.time.LocalDate.of(2026, 9, 1), 128000L, 4096L,
                "text,image", "text", "[\"tools\"]", "[\"ocr\"]",
                "desc", "{\"a\":1}",
                "{\"temperature\":0.7,\"seed\":\"42\"}",
                "pv2", "o4-mini-up", null, 80,
                new java.math.BigDecimal("1.2"), new java.math.BigDecimal("1.5"), true,
                "[{\"providerId\":\"pv3\",\"providerModelId\":\"alt\",\"weight\":20,\"enabled\":false}]",
                "{\"tiers\":[{\"input\":1.1,\"output\":4.4}],\"currency\":\"CNY\",\"priceVersion\":\"v2\"}",
                java.time.LocalDateTime.of(2026, 10, 1, 12, 30),
                java.time.LocalDateTime.of(2026, 10, 5, 8, 0), "admin");
        when(modelService.getByCode("openai/o4-mini")).thenReturn(detail);
        when(providerService.listAll()).thenReturn(java.util.List.of(openai()));
        when(routeService.listAll()).thenReturn(java.util.List.of(
                route("r1", "PUBLISHED", "openai/o4-mini")));

        // UI 编码 ~ 还原为 /
        ModelRecord record = assembler.detail("openai~o4-mini");

        assertThat(record.code()).isEqualTo("openai~o4-mini");
        assertThat(record.providerCode()).isEqualTo("openai-main");
        assertThat(record.providerName()).isEqualTo("OpenAI");
        assertThat(record.currency()).isEqualTo("CNY");
        assertThat(record.priceVersion()).isEqualTo("v2");
        assertThat(record.releaseDate()).isEqualTo("2026-09-01");
        assertThat(record.inputModalities()).containsExactly("text", "image");
        assertThat(record.modalityPreview()).isEqualTo("T+I → T");
        assertThat(record.inputModalitiesText()).isEqualTo("text · image");
        assertThat(record.tagsText()).isEqualTo("ocr");
        assertThat(record.description()).isEqualTo("desc");
        assertThat(record.metadata()).isEqualTo("{\"a\":1}");
        assertThat(record.params().temperature()).isEqualTo("0.7");
        assertThat(record.params().seed()).isEqualTo("42");
        assertThat(record.params().topP()).isEqualTo("—");
        assertThat(record.params().capabilities()).containsExactly("tools");
        assertThat(record.suppliers()).hasSize(2);
        assertThat(record.suppliers().get(0).providerModelId()).isEqualTo("o4-mini-up");
        assertThat(record.suppliers().get(0).weight()).isEqualTo(80);
        assertThat(record.suppliers().get(0).costRate()).isEqualByComparingTo("1.2");
        assertThat(record.suppliers().get(0).billingRate()).isEqualByComparingTo("1.5");
        assertThat(record.suppliers().get(0).keyConfigured()).isTrue();
        assertThat(record.suppliers().get(1).status()).isEqualTo("disabled");
        assertThat(record.suppliers().get(1).providerCode()).isEqualTo("—");
        assertThat(record.supplierWeightTotal()).isEqualTo("80");
        assertThat(record.primaryUpstreamId()).isEqualTo("o4-mini-up");
        assertThat(record.providerSummary()).isEqualTo("OpenAI（主） · 共 2 个供应商模型");
        assertThat(record.routeRefs()).hasSize(1);
        assertThat(record.routeRefs().get(0).status()).isEqualTo("启用");
        assertThat(record.routeRefs().get(0).type()).isEqualTo("单模型");
        assertThat(record.routeSummary()).isEqualTo("1 条路由 · 1 条活跃");
        assertThat(record.shortDate()).isEqualTo("10-05");
        assertThat(record.operator()).isEqualTo("admin");
        assertThat(record.pricingSummary()).isEqualTo("$1.1 / $4.4 /M");
    }

    @Test
    void optionsReturnOnlyEnabledModels() {
        stubRelations();

        var options = assembler.options();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).value()).isEqualTo("openai/o4-mini");
        assertThat(options.get(0).label()).isEqualTo("o4 mini（openai/o4-mini）");
    }
}
