package com.innospots.nexmux.console.ops;

import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.CostRecordVo;
import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.ops.service.CostQueryService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
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
 * {@link CostViewAssembler} 单测：pricing_snapshot 展开、成本计算过程文案、关联名称 join。
 */
class CostViewAssemblerTest {

    private CostQueryService costQueryService;
    private CostViewAssembler assembler;

    @BeforeEach
    void setUp() {
        costQueryService = Mockito.mock(CostQueryService.class);
        ClientService clientService = Mockito.mock(ClientService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        ProviderService providerService = Mockito.mock(ProviderService.class);
        assembler = new CostViewAssembler(costQueryService, clientService, modelService, providerService,
                DisplayFormatsProvider.defaults());

        when(clientService.listAll()).thenReturn(List.of(
                new ClientVo("c1", "app-web", "Web 应用", "ENABLED", List.of(),
                        "[]", "[]", 1, "ak-1", "[]", null, null, null)));
        when(modelService.listAll()).thenReturn(List.of(
                new ModelVo("m1", "zai/glm-5", "GLM-5", "ENABLED", "LLM",
                        null, null, "pv1", null, null)));
        when(providerService.listAll()).thenReturn(List.of(
                new ProviderVo("pv1", "zai", "Z.AI", "COMMERCIAL", "openai-completions",
                        "zai", "zai", null, "https://api.z.ai", null, 60000, "ENABLED", "none", 0)));
    }

    private static CostRecordVo cost(String snapshot) {
        return new CostRecordVo(
                "req-1", LocalDateTime.now().minusMinutes(5), "web", "c1", "ak-1", "general",
                "zai/glm-5", "zai", 2_000_000L, 500_000L, 100_000L, null, null, snapshot);
    }

    @Test
    void expandsPricingSnapshotAndComputesCosts() {
        when(costQueryService.page(any(CostPageRequest.class))).thenReturn(PageResult.of(
                List.of(cost("{\"currency\":\"USD\",\"priceVersion\":\"v2\",\"priceEffective\":\"2026-10-01\","
                        + "\"tierUpto\":null,\"tierInputPrice\":1.0,\"tierOutputPrice\":4.0,"
                        + "\"tierCacheReadPrice\":0.1,\"multiplier\":2,\"multiplierRule\":\"时段倍率 ×2\"}")),
                1, 5000, 1));

        CostListResult result = assembler.assemble(new CostPageRequest(
                null, null, null, null, null, null, null, null, null));

        CostRecord row = result.items().get(0);
        assertThat(row.clientName()).isEqualTo("Web 应用");
        assertThat(row.modelName()).isEqualTo("GLM-5");
        assertThat(row.providerName()).isEqualTo("Z.AI");
        assertThat(row.priceVersion()).isEqualTo("v2");
        assertThat(row.tierLabel()).isEqualTo("∞");
        assertThat(row.tierDetail()).isEqualTo("UPTO ∞（无上限档）");
        assertThat(row.multiplierText()).isEqualTo("2x");
        assertThat(row.multiplierRule()).isEqualTo("时段倍率 ×2");
        // 2M tokens × $1 × 2 ÷ 1M = $4；0.5M × $4 × 2 ÷ 1M = $4；100K × $0.1 × 2 ÷ 1M = $0.02
        assertThat(row.inputCost()).isEqualTo("4.0000");
        assertThat(row.outputCost()).isEqualTo("4.0000");
        assertThat(row.cacheCost()).isEqualTo("0.0200");
        // vo.costUsd 为空 → 合计由分项相加
        assertThat(row.totalCost()).isEqualTo("8.0200");
        assertThat(row.inputCalcText()).isEqualTo("2,000,000 × $1 × 2 ÷ 1M = $4.0000");
        assertThat(row.outputCalcText()).isEqualTo("500,000 × $4 × 2 ÷ 1M = $4.0000");
        assertThat(row.cacheCalcText()).isEqualTo("100,000 × $0.1 × 2 ÷ 1M = $0.0200");
        assertThat(row.minutesAgo()).isEqualTo(5);
        assertThat(row.contextSummary()).contains("Web 应用（ak-1）· GLM-5 · Z.AI");
    }

    @Test
    void fallsBackWhenSnapshotMissing() {
        when(costQueryService.page(any(CostPageRequest.class))).thenReturn(PageResult.of(
                List.of(cost(null)), 1, 5000, 1));

        CostListResult result = assembler.assemble(new CostPageRequest(
                null, null, null, null, null, null, null, null, null));

        CostRecord row = result.items().get(0);
        assertThat(row.priceVersion()).isEqualTo("—");
        assertThat(row.currency()).isEqualTo("USD");
        assertThat(row.multiplierRule()).isEqualTo("未命中时段倍率（按 1.0 计）");
        assertThat(row.inputCost()).isEqualTo("0.0000");
        // 无快照 → 缓存单价不可得，缓存成本按 "—" 展示
        assertThat(row.cacheCost()).isEqualTo("—");
        assertThat(row.cacheCalcText()).isEqualTo("无缓存用量");
        assertThat(row.totalCost()).isEqualTo("0.0000");
    }
}
