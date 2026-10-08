package com.innospots.nexmux.console.ops;

import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogDetailVo;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.ops.service.RequestLogQueryService;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexus.base.domain.response.PageResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link CallLogViewAssembler} 单测：关联名称 join、状态/鉴权规则化、金额与 tokens 文案。
 */
class CallLogViewAssemblerTest {

    private RequestLogQueryService requestLogQueryService;
    private CallLogViewAssembler assembler;

    @BeforeEach
    void setUp() {
        requestLogQueryService = Mockito.mock(RequestLogQueryService.class);
        ClientService clientService = Mockito.mock(ClientService.class);
        RouteService routeService = Mockito.mock(RouteService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        ProviderService providerService = Mockito.mock(ProviderService.class);
        assembler = new CallLogViewAssembler(
                requestLogQueryService, clientService, routeService, modelService, providerService);

        when(clientService.listAll()).thenReturn(List.of(
                new ClientVo("c1", "app-web", "Web 应用", "ENABLED", List.of(),
                        "[]", "[]", 1, "ak-1", "[]", null, null, null)));
        when(routeService.listAll()).thenReturn(List.of(
                new RouteVo("r1", "general-chat", "general", "通用对话", "PUBLISHED",
                        new RouteSpec("general", "/v1/chat/completions", "WEIGHTED",
                                List.of(new RouteCandidate("zai/glm-5", 100)), true, 1), null)));
        when(modelService.listAll()).thenReturn(List.of(
                new ModelVo("m1", "zai/glm-5", "GLM-5", "ENABLED", "LLM",
                        null, null, "pv1", null, null)));
        when(providerService.listAll()).thenReturn(List.of(
                new ProviderVo("pv1", "zai", "Z.AI", "COMMERCIAL", "openai-completions",
                        "zai", "zai", null, "https://api.z.ai", null, 60000, "ENABLED", "none", 0)));
    }

    private static RequestLogVo log(String id, String status, Integer httpStatus) {
        return new RequestLogVo(id, "c1", "r1", "zai/glm-5", status, 820L,
                null, null, LocalDateTime.of(2026, 10, 5, 8, 0, 0), "ak-1", "u@x.com",
                "zai", "openai-completions", "default", "web", "general", httpStatus,
                1500L, 600L, 120L, 30L,
                new BigDecimal("0.0012"), new BigDecimal("0.0030"));
    }

    @Test
    void assemblesRowWithJoinedNamesAndComputedTexts() {
        when(requestLogQueryService.page(any(RequestLogPageRequest.class)))
                .thenReturn(PageResult.of(List.of(log("req-1", "SUCCESS", 200)), 1, 5000, 1));

        CallLogListResult result = assembler.assemble(new RequestLogPageRequest(
                null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.filteredText()).isEqualTo("1");
        CallLogRecord row = result.items().get(0);
        assertThat(row.id()).isEqualTo("req-1");
        assertThat(row.status()).isEqualTo("success");
        assertThat(row.statusLabel()).isEqualTo("成功");
        assertThat(row.clientName()).isEqualTo("Web 应用");
        assertThat(row.routeCode()).isEqualTo("general-chat");
        assertThat(row.routeName()).isEqualTo("通用对话");
        assertThat(row.strategyText()).isEqualTo("加权轮询");
        assertThat(row.modelName()).isEqualTo("GLM-5");
        assertThat(row.providerName()).isEqualTo("Z.AI");
        assertThat(row.usageText()).isEqualTo("1.5K/600");
        assertThat(row.inputTokensText()).isEqualTo("1,500");
        assertThat(row.totalTokensText()).isEqualTo("2,100");
        assertThat(row.costText()).isEqualTo("0.0012");
        assertThat(row.billingText()).isEqualTo("0.0030");
        assertThat(row.marginText()).isEqualTo("0.0018");
        assertThat(row.latencyText()).isEqualTo("820ms");
        assertThat(row.timeShort()).isEqualTo("10-05 08:00:00 UTC");
        assertThat(row.authResult()).isEqualTo("通过（AppKey 有效）");
        assertThat(row.channel()).isEqualTo("—");
        assertThat(row.error()).isNull();
    }

    @Test
    void flagsAuthFailureByHttpStatus() {
        when(requestLogQueryService.page(any(RequestLogPageRequest.class)))
                .thenReturn(PageResult.of(List.of(log("req-2", "ERROR", 401)), 1, 5000, 1));

        CallLogListResult result = assembler.assemble(new RequestLogPageRequest(
                null, null, null, null, "ERROR", null, null, null, null, null, null));

        CallLogRecord row = result.items().get(0);
        assertThat(row.status()).isEqualTo("error");
        assertThat(row.statusLabel()).isEqualTo("错误");
        assertThat(row.authResult()).isEqualTo("失败：AppKey 校验未通过");
        assertThat(row.httpStatusText()).isEqualTo("401");
    }

    @Test
    void assemblesDetailWithUpstreamAndPricingTexts() {
        RequestLogDetailVo detail = new RequestLogDetailVo(
                "req-1", "c1", "r1", "zai/glm-5", "SUCCESS", 820L,
                null, "[{\"name\":\"route\",\"input\":\"in\",\"decision\":\"d\",\"output\":\"out\",\"duration\":\"1ms\",\"reason\":\"r\"}]",
                LocalDateTime.of(2026, 10, 5, 8, 0, 0), "ak-1", "u@x.com",
                "zai", "openai-completions", "default", "web", "general", 200,
                1500L, 600L, 120L, 30L, new BigDecimal("0.0012"), new BigDecimal("0.0030"),
                95L, "glm-5-up", "https://api.z.ai/chat", "200", 640L, 1,
                "1.2.3.4", "POST", "/v1/chat/completions", "trace-1", "UPSTREAM_4XX",
                "{\"priceVersion\":\"v2\",\"priceEffective\":\"2026-10-01\",\"costRate\":1.2,\"billingRate\":1.5}",
                "{\"policy\":\"masked\",\"policyNote\":\"按策略未留存\"}",
                LocalDateTime.of(2026, 10, 5, 8, 0, 0));
        when(requestLogQueryService.get("req-1")).thenReturn(detail);

        CallLogRecord record = assembler.detail("req-1");

        assertThat(record.ttftText()).isEqualTo("95ms");
        assertThat(record.upstreamModel()).isEqualTo("glm-5-up");
        assertThat(record.upstreamLatencyText()).isEqualTo("640ms");
        assertThat(record.retriesText()).isEqualTo("1");
        assertThat(record.traceId()).isEqualTo("trace-1");
        assertThat(record.pricingSnapshot())
                .isEqualTo("v2（2026-10-01 生效） · C=1.2 · M=1.5 · 无时段倍率");
        assertThat(record.pr().policy()).isEqualTo("masked");
        assertThat(record.pr().prompt()).isNull();
        assertThat(record.trace()).hasSize(1);
        assertThat(record.trace().get(0).name()).isEqualTo("route");
    }
}
