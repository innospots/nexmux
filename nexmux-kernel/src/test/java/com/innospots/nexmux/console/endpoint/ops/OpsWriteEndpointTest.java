package com.innospots.nexmux.console.endpoint.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.model.service.ModelService;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.route.service.RouteService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpsWriteEndpoint} 单测：code → id 解析、表单整包提交的契约字段提取
 * （缺省回填 / 数组与分隔文本兼容 / 创建后补启用）。
 */
class OpsWriteEndpointTest {

    private ProviderService providerService;
    private ModelService modelService;
    private RouteService routeService;
    private OpsWriteEndpoint endpoint;

    @BeforeEach
    void setUp() {
        providerService = Mockito.mock(ProviderService.class);
        modelService = Mockito.mock(ModelService.class);
        routeService = Mockito.mock(RouteService.class);
        endpoint = new OpsWriteEndpoint(providerService, modelService, routeService);
        when(providerService.getByCode("zai")).thenReturn(new ProviderDetailVo(
                "pv1", "zai", "Z.AI", "COMMERCIAL", "openai-completions", "zai", "zai",
                null, "https://api.z.ai", null, 60000, null, null, null,
                "ENABLED", "none", null, null, List.of(), null, null, null));
        when(modelService.getByCode("zai/glm-5")).thenReturn(new ModelDetailVo(
                "m1", "zai/glm-5", "GLM-5", "LLM", "DRAFT", null, null, null, null,
                null, null, null, null, null, null, null, "pv1", "glm-5-up", null,
                null, null, null, null, null, null, null, null, null));
    }

    private static JsonNode json(String text) {
        return NexmuxJson.read(text, JsonNode.class);
    }

    @Test
    void resolvesProviderCodeToIdForMutations() {
        endpoint.providerEnable("zai");
        verify(providerService).setEnabled("pv1", true);

        endpoint.providerDisable("zai");
        verify(providerService).disable("pv1");

        endpoint.providerDelete("zai");
        verify(providerService).delete("pv1");
    }

    @Test
    void saveFiltersToContractFields() {
        endpoint.providerSave("zai", json(
                "{\"displayName\":\"Z.AI New\",\"baseUrl\":\"https://b\",\"region\":\"cn\","
                        + "\"timeoutMs\":30000,\"apiKey\":\"secret-ignored\",\"proxy\":\"ignored\"}"));

        ArgumentCaptor<ProviderUpdateRequest> captor = ArgumentCaptor.forClass(ProviderUpdateRequest.class);
        verify(providerService).update(Mockito.eq("pv1"), captor.capture());
        ProviderUpdateRequest request = captor.getValue();
        assertThat(request.displayName()).isEqualTo("Z.AI New");
        assertThat(request.baseUrl()).isEqualTo("https://b");
        assertThat(request.region()).isEqualTo("cn");
        assertThat(request.timeoutMs()).isEqualTo(30000);
        assertThat(request.proxy()).isEqualTo("ignored");
    }

    @Test
    void createBackfillsVendorCodes() {
        endpoint.providerCreate(json(
                "{\"providerCode\":\"my-p\",\"displayName\":\"Mine\",\"providerType\":\"COMMERCIAL\","
                        + "\"defaultProtocol\":\"openai-completions\",\"baseUrl\":\"https://b\"}"));

        ArgumentCaptor<ProviderCreateRequest> captor = ArgumentCaptor.forClass(ProviderCreateRequest.class);
        verify(providerService).create(captor.capture());
        assertThat(captor.getValue().vendorCode()).isEqualTo("my-p");
        assertThat(captor.getValue().sourceVendorCode()).isEqualTo("my-p");
    }

    @Test
    void resolvesUiModelCodeWithTilde() {
        endpoint.modelDisable("zai~glm-5");
        verify(modelService).disable("m1");
    }

    @Test
    void modelSaveParsesModalitiesFromCsvText() {
        endpoint.modelSave("zai~glm-5", json(
                "{\"displayName\":\"GLM-5\",\"contextLength\":128000,\"inputModalities\":\"text, image\","
                        + "\"outputModalities\":\"text\",\"tags\":\"fast · cheap\"}"));

        ArgumentCaptor<ModelUpdateRequest> captor = ArgumentCaptor.forClass(ModelUpdateRequest.class);
        verify(modelService).update(Mockito.eq("m1"), captor.capture());
        ModelUpdateRequest request = captor.getValue();
        assertThat(request.contextLength()).isEqualTo(128000L);
        assertThat(request.inputModalities()).containsExactly("text", "image");
        assertThat(request.outputModalities()).containsExactly("text");
        assertThat(request.tags()).containsExactly("fast", "cheap");
    }

    @Test
    void modelSaveParsesDefaultParamsMetadataAndCapabilities() {
        endpoint.modelSave("zai~glm-5", json("""
                {"metadata":{"owner":"e2e"},
                 "temperature":0.42,"maxTokensParam":3,"topP":0.7,
                 "capabilities":"Streaming, Tool Calling"}
                """));

        ArgumentCaptor<ModelUpdateRequest> captor = ArgumentCaptor.forClass(ModelUpdateRequest.class);
        verify(modelService).update(Mockito.eq("m1"), captor.capture());
        ModelUpdateRequest request = captor.getValue();
        assertThat(request.metadataSpec()).contains("owner");
        assertThat(request.defaultParams()).contains("\"temperature\":0.42")
                .contains("\"maxTokens\":3").contains("\"topP\":0.7");
        assertThat(request.capabilityTags()).contains("Streaming").contains("Tool Calling");
    }

    @Test
    void modelSaveParsesBindingAndPricingConfiguration() {
        endpoint.modelSave("zai~glm-5", json("""
                {"primaryBindingWeight":80,"primaryCostMultiplier":2,"primaryReferenceMultiplier":3,
                 "primaryBindingEnabled":"off","extraBindings":[{"providerModelId":"stub-b","weight":20}],
                 "pricingSpec":{"currency":"USD","tiers":[{"upto":10,"input":1,"output":2}]}}
                """));

        ArgumentCaptor<ModelUpdateRequest> captor = ArgumentCaptor.forClass(ModelUpdateRequest.class);
        verify(modelService).update(Mockito.eq("m1"), captor.capture());
        ModelUpdateRequest request = captor.getValue();
        assertThat(request.primaryBindingWeight()).isEqualTo(80);
        assertThat(request.primaryCostMultiplier()).isEqualByComparingTo("2");
        assertThat(request.primaryReferenceMultiplier()).isEqualByComparingTo("3");
        assertThat(request.primaryBindingEnabled()).isFalse();
        assertThat(request.extraBindings()).contains("stub-b");
        assertThat(request.pricingSpec()).contains("tiers");
    }

    @Test
    void modelCreateEnablesAfterCreateWhenFormSaysEnabled() {
        when(modelService.create(Mockito.any(ModelCreateRequest.class))).thenReturn(
                new ModelVo("m2", "new-model", "New", "DRAFT", "LLM",
                        null, null, "pv1", null, null));

        endpoint.modelCreate(json(
                "{\"modelCode\":\"new-model\",\"displayName\":\"New\",\"modelType\":\"LLM\","
                        + "\"primaryProviderCode\":\"zai\",\"status\":\"enabled\"}"));

        verify(modelService).setEnabled("m2", true);
    }

    @Test
    void routeCreateMapsFormFields() {
        endpoint.routeCreate(json(
                "{\"code\":\"general\",\"alias\":\"general\",\"name\":\"通用\",\"path\":\"/v1/chat\","
                        + "\"protocol\":\"OpenAI / Chat Completions\",\"strategy\":\"WEIGHTED\","
                        + "\"stickySession\":\"on\",\"healthFilter\":\"off\",\"tags\":\"prod, gray\"}"));

        ArgumentCaptor<RouteCreateRequest> captor = ArgumentCaptor.forClass(RouteCreateRequest.class);
        verify(routeService).create(captor.capture());
        RouteCreateRequest request = captor.getValue();
        assertThat(request.routeCode()).isEqualTo("general");
        assertThat(request.displayName()).isEqualTo("通用");
        assertThat(request.path()).isEqualTo("/v1/chat");
        assertThat(request.strategyType()).isEqualTo("WEIGHTED");
        assertThat(request.stickySession()).isTrue();
        assertThat(request.healthFilter()).isFalse();
        assertThat(request.tags()).containsExactly("prod", "gray");
        assertThat(request.models()).isEmpty();
    }

    @Test
    void routeSaveMergesFormOverExistingSpecKeepingCandidates() {
        RouteSpec existing = new RouteSpec("general", "/v1/chat", "FIXED",
                java.util.List.of(new RouteCandidate("zai/glm-5", 100)), true, 1,
                "old-desc", "OpenAI / Chat Completions", java.util.List.of("prod"), null);
        when(routeService.getByCode("general")).thenReturn(new RouteDetailVo(
                "r1", "general", "general", "通用", "DRAFT", existing, null,
                null, null, null, null, null, null));

        endpoint.routeSave("general", json(
                "{\"name\":\"通用 v2\",\"strategy\":\"WEIGHTED\",\"stickySession\":\"on\"}"));

        ArgumentCaptor<RouteUpdateRequest> captor = ArgumentCaptor.forClass(RouteUpdateRequest.class);
        verify(routeService).updateDraft(Mockito.eq("r1"), captor.capture());
        RouteUpdateRequest request = captor.getValue();
        assertThat(request.displayName()).isEqualTo("通用 v2");
        assertThat(request.routeSpec().strategyType()).isEqualTo("WEIGHTED");
        assertThat(request.routeSpec().stickySession()).isTrue();
        // 未提交的字段保留现状，候选模型不被清空
        assertThat(request.routeSpec().ingress()).isEqualTo("/v1/chat");
        assertThat(request.routeSpec().protocol()).isEqualTo("OpenAI / Chat Completions");
        assertThat(request.routeSpec().candidates()).hasSize(1);
        assertThat(request.routeSpec().tags()).containsExactly("prod");
    }

    @Test
    void routeMutationsResolveCodeToId() {
        when(routeService.getByCode("general")).thenReturn(new RouteDetailVo(
                "r1", "general", "general", "通用", "DISABLED", null, null,
                null, null, null, null, null, null));

        endpoint.routeEnable("general");
        verify(routeService).enable("r1");

        endpoint.routeDelete("general");
        verify(routeService).delete("r1");
    }
}
