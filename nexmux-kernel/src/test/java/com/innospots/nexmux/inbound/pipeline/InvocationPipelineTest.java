package com.innospots.nexmux.inbound.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.security.AppKeySupport;
import com.innospots.nexmux.core.shared.security.CredentialCipher;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.protocol.UpstreamChatAdapter;
import com.innospots.nexmux.inbound.runtime.BindingHealthRegistry;
import com.innospots.nexmux.inbound.runtime.PublishedRouteCache;
import com.innospots.nexmux.inbound.runtime.RuntimeCatalogCache;
import com.innospots.nexus.base.exception.NexusException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class InvocationPipelineTest {

    private static final String PLAIN_KEY = "gw-sk-test";
    private static final String INGRESS = "/v1/chat/completions";

    private RequestLogDao requestLogDao;
    private UpstreamChatAdapter chatAdapter;
    private BindingHealthRegistry healthRegistry;
    private InvocationPipeline pipeline;
    private ModelDao modelDao;
    private RuntimeCatalogCache catalogCache;
    private ModelEntity model;
    private RouteDao routeDao;
    private PublishedRouteCache routeCache;

    @BeforeEach
    void setUp() {
        routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute()));
        routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();

        modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        model = model();
        when(modelDao.selectList(null)).thenReturn(List.of(model));
        when(providerDao.selectList(null)).thenReturn(List.of(provider()));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential()));
        when(clientDao.selectList(null)).thenReturn(List.of(client()));
        catalogCache = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        catalogCache.refresh();

        requestLogDao = Mockito.mock(RequestLogDao.class);
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        chatAdapter = Mockito.mock(UpstreamChatAdapter.class);
        healthRegistry = new BindingHealthRegistry(3, 10000);
        pipeline = new InvocationPipeline(catalogCache, routeCache, healthRegistry,
                chatAdapter, new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, new CredentialCipher(null));
    }

    private static RouteEntity publishedRoute() {
        return publishedRoute(true);
    }

    private static RouteEntity publishedRoute(boolean filterUnhealthy) {
        RouteEntity route = new RouteEntity();
        route.setRouteId("rt-1");
        route.setAlias("general");
        route.setStatus("PUBLISHED");
        route.setPublishedRouteSpec("{\"alias\":\"general\",\"ingress\":\"/v1/chat/completions\","
                + "\"strategyType\":\"FIXED\","
                + "\"candidates\":[{\"modelCode\":\"openai/o4-mini\",\"weight\":100}],"
                + "\"filterUnhealthyBindings\":" + filterUnhealthy + "}");
        return route;
    }

    private static ModelEntity model() {
        ModelEntity model = new ModelEntity();
        model.setModelId("md-1");
        model.setModelCode("openai/o4-mini");
        model.setStatus("ENABLED");
        model.setPrimaryProviderId("pv-1");
        model.setPrimaryProviderModelId("o4-mini");
        model.setPricingSpec("{\"currency\":\"USD\",\"input\":10,\"output\":30}");
        return model;
    }

    private static ProviderEntity provider() {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderId("pv-1");
        provider.setProviderCode("openai");
        provider.setStatus("ENABLED");
        provider.setBaseUrl("http://upstream.test/v1");
        provider.setTimeoutMs(5000);
        return provider;
    }

    private static ProviderCredentialEntity credential() {
        ProviderCredentialEntity credential = new ProviderCredentialEntity();
        credential.setProviderId("pv-1");
        credential.setCiphertext("sk-upstream");
        return credential;
    }

    private static ClientEntity client() {
        AppKeySupport.Material material = AppKeySupport.generate();
        ClientEntity client = new ClientEntity();
        client.setClientId("cl-1");
        client.setClientCode("app-a");
        client.setDisplayName("应用 A");
        client.setStatus("ENABLED");
        client.setAuthorizedRouteIds("[\"rt-1\"]");
        client.setAppKeyHash(AppKeySupport.hash(PLAIN_KEY, material.salt()));
        client.setAppKeySalt(material.salt());
        return client;
    }

    private static Map<String, Object> chatBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "general");
        body.put("messages", List.of(Map.of("role", "user", "content", "你好")));
        return body;
    }

    private void stubUpstreamOk() throws IOException {
        when(chatAdapter.chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn("{\"id\":\"c1\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"你好！\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");
    }

    @Test
    void invokeRejectsDisabledModelWhenHealthFilterDisabled() {
        model.setStatus("DISABLED");
        catalogCache.refresh();
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute(false)));
        routeCache.refresh();

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, error ->
                        assertThat(error.code()).isEqualTo(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()));
    }

    @Test
    void invokeMergesModelDefaultsBeforeExplicitParameters() throws IOException {
        model.setDefaultParams("{\"temperature\":0.42,\"maxTokens\":3,\"topP\":0.7}");
        catalogCache.refresh();
        stubUpstreamOk();
        Map<String, Object> body = chatBody();
        body.put("temperature", 0.9);

        pipeline.invoke(PLAIN_KEY, body, INGRESS);

        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(chatAdapter).chatCompletions(anyString(), anyString(), bodyCaptor.capture(),
                anyInt(), anyInt(), any());
        JsonNode upstream = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bodyCaptor.getValue());
        assertThat(upstream.get("temperature").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(upstream.get("max_tokens").intValue()).isEqualTo(3);
        assertThat(upstream.get("top_p").decimalValue()).isEqualByComparingTo("0.7");
    }

    @Test
    void invokeRejectsToolsWhenModelHasNoToolCapability() {
        Map<String, Object> body = chatBody();
        body.put("tools", List.of(Map.of("type", "function")));

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, body, INGRESS))
                .isInstanceOfSatisfying(NexusException.class, error ->
                        assertThat(error.code()).isEqualTo(NexmuxStatusCode.INVALID_REQUEST.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void invokeAllowsToolsWhenModelDeclaresToolCapability() throws IOException {
        model.setCapabilityTags("[\"Tool Calling\"]");
        catalogCache.refresh();
        stubUpstreamOk();
        Map<String, Object> body = chatBody();
        body.put("tools", List.of(Map.of("type", "function")));

        InvocationPipeline.InvocationResult result = pipeline.invoke(PLAIN_KEY, body, INGRESS);

        assertThat(result.httpStatus()).isEqualTo(200);
    }

    @Test
    void invokeRejectsUnsupportedUpstreamProtocol() {
        model.setPrimaryUpstreamProtocol("anthropic-messages");
        catalogCache.refresh();

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, error ->
                        assertThat(error.code()).isEqualTo(NexmuxStatusCode.INVALID_REQUEST.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void invokeHappyPathWritesRuntimeLog() throws IOException {
        stubUpstreamOk();

        InvocationPipeline.InvocationResult result = pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS);

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.tokensIn()).isEqualTo(10);
        assertThat(result.tokensOut()).isEqualTo(5);
        // 上游 model 已替换为绑定 ID，凭证来自目录缓存
        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(chatAdapter).chatCompletions(
                org.mockito.ArgumentMatchers.eq("http://upstream.test/v1"),
                org.mockito.ArgumentMatchers.eq("sk-upstream"),
                bodyCaptor.capture(), anyInt(), anyInt(), any());
        assertThat(bodyCaptor.getValue()).contains("\"model\":\"o4-mini\"");

        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "RUNTIME".equals(log.getSource())
                        && "cl-1".equals(log.getClientId())
                        && "rt-1".equals(log.getRouteId())
                        && "general".equals(log.getAlias())
                        && "SUCCESS".equals(log.getStatus())
                        && log.getAppKeyId() != null && log.getAppKeyId().startsWith("gw-ak-")
                        && Long.valueOf(10).equals(log.getInputTokens())
                        && new BigDecimal("0.00025000").compareTo(log.getCostUsd()) == 0
                        && log.getRouteTrace().contains("\"strategy\":\"FIXED\"")
                        && log.getRouteTrace().contains("\"providerId\":\"pv-1\"")
                        && log.getPricingSnapshot() != null));
    }

    @Test
    void rejectsInvalidAppKey() {
        assertThatThrownBy(() -> pipeline.invoke("gw-sk-bad", chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
    }

    @Test
    void rejectsMissingModelAlias() {
        Map<String, Object> body = chatBody();
        body.remove("model");

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, body, INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.INVALID_REQUEST.fullCode()));
    }

    @Test
    void rejectsUnknownAlias() {
        Map<String, Object> body = chatBody();
        body.put("model", "ghost");

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, body, INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.ROUTE_NOT_FOUND.fullCode()));
    }

    @Test
    void rejectsIngressMismatch() {
        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, chatBody(), "/v1/other"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.ROUTE_NOT_FOUND.fullCode()));
    }

    @Test
    void rejectsUpstreamFailureWithErrorLog() throws IOException {
        when(chatAdapter.chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new IOException("HTTP 500: boom"));

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.UPSTREAM_FAILURE.fullCode()));
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "ERROR".equals(log.getStatus()) && "HTTP_502".equals(log.getErrorCode())
                        && "RUNTIME".equals(log.getSource())));
    }

    @Test
    void streamHappyPathPipesAndSettles() throws IOException {
        Mockito.when(chatAdapter.streamChatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any(),
                        any(OutputStream.class)))
                .thenAnswer(invocation -> {
                    OutputStream out = invocation.getArgument(6);
                    out.write("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                            .getBytes(StandardCharsets.UTF_8));
                    out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    return new UpstreamChatAdapter.StreamOutcome("你好", 8L, 4L, 120,
                            "{\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":4}}");
                });
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InvocationPipeline.InvocationResult result =
                pipeline.invokeStream(PLAIN_KEY, chatBody(), INGRESS, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("你好").contains("[DONE]");
        assertThat(result.tokensIn()).isEqualTo(8);
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "SUCCESS".equals(log.getStatus()) && Long.valueOf(120).equals(log.getTtftMs())));
    }

    @Test
    void hardQuotaBlocksBeforeUpstream() throws IOException {
        ClientEntity limited = client();
        limited.setQuotaSpec("[{\"name\":\"q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":0,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        when(clientDao.selectList(null)).thenReturn(List.of(limited));
        ModelDao modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of(model()));
        when(providerDao.selectList(null)).thenReturn(List.of(provider()));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential()));
        RuntimeCatalogCache limitedCatalog = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        limitedCatalog.refresh();
        RouteDao routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute()));
        PublishedRouteCache routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        InvocationPipeline limitedPipeline = new InvocationPipeline(limitedCatalog, routeCache,
                new BindingHealthRegistry(3, 10000), chatAdapter,
                new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, new CredentialCipher(null));

        assertThatThrownBy(() -> limitedPipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    /** 以指定路由快照重建 pipeline（共用 catalog / healthRegistry / mock adapter）。 */
    private InvocationPipeline pipelineWithRoute(RouteEntity route) {
        return pipelineWith(route, model(), provider());
    }

    /** 全夹具变体：可定制路由 / 模型 / 供应商（G-01/G-03/G-06 等场景）。 */
    private InvocationPipeline pipelineWith(RouteEntity route, ModelEntity model, ProviderEntity provider) {
        return pipelineWith(route, model, provider, client());
    }

    /** 全夹具变体（含客户端定制：配额 / 限流场景）。 */
    private InvocationPipeline pipelineWith(RouteEntity route, ModelEntity model,
                                            ProviderEntity provider, ClientEntity client) {
        RouteDao routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(route));
        PublishedRouteCache routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();
        ModelDao modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of(model));
        when(providerDao.selectList(null)).thenReturn(List.of(provider));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential()));
        when(clientDao.selectList(null)).thenReturn(List.of(client));
        RuntimeCatalogCache catalog = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        catalog.refresh();
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        return new InvocationPipeline(catalog, routeCache, healthRegistry,
                chatAdapter, new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, new CredentialCipher(null));
    }

    private void markBindingUnhealthy() {
        BindingHealthRegistry.Target target = new BindingHealthRegistry.Target("pv-1", "o4-mini");
        healthRegistry.recordFailure(target, "e");
        healthRegistry.recordFailure(target, "e");
        healthRegistry.recordFailure(target, "e");
    }

    @Test
    void filterUnhealthyTrueRejectsBeforeUpstream() throws IOException {
        // 健康过滤开启：不健康绑定被剔除，503 NMX020005，不调上游、不落 ERROR 日志
        InvocationPipeline p = pipelineWithRoute(publishedRoute(true));
        markBindingUnhealthy();

        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void filterUnhealthyFalseCallsUpstreamAndLogsError() throws IOException {
        // 健康过滤关闭：不健康绑定仍被选中并真实调上游，失败后 502 NMX060001 + ERROR 日志（PRD §19.7）
        InvocationPipeline p = pipelineWithRoute(publishedRoute(false));
        markBindingUnhealthy();
        when(chatAdapter.chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.UPSTREAM_FAILURE.fullCode()));
        Mockito.verify(chatAdapter).chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any());
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "ERROR".equals(log.getStatus()) && "HTTP_502".equals(log.getErrorCode())
                        && "RUNTIME".equals(log.getSource())));
    }

    @Test
    void resyncQuotasBackfillsCountersFromRequestLog() throws IOException {        // 客户端配额 limit=5；库里已有 5 次 RUNTIME 调用 → 校准后新请求直接 429
        ClientEntity limited = client();
        limited.setQuotaSpec("[{\"name\":\"q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":5,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        when(clientDao.selectList(null)).thenReturn(List.of(limited));
        ModelDao modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of(model()));
        when(providerDao.selectList(null)).thenReturn(List.of(provider()));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential()));
        RuntimeCatalogCache limitedCatalog = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        limitedCatalog.refresh();
        RouteDao routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute()));
        PublishedRouteCache routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        when(requestLogDao.selectMaps(any())).thenReturn(List.of(Map.of(
                "requestCount", 5L, "inputTokens", 0L, "outputTokens", 0L, "costUsd", BigDecimal.ZERO)));
        InvocationPipeline resyncPipeline = new InvocationPipeline(limitedCatalog, routeCache,
                new BindingHealthRegistry(3, 10000), chatAdapter,
                new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, new CredentialCipher(null));

        resyncPipeline.resyncQuotas();

        assertThatThrownBy(() -> resyncPipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void resyncQuotasToleratesLowercaseAggregateAliases() throws IOException {
        // 复现：H2 DATABASE_TO_LOWER=TRUE 下未加引号的聚合别名被小写化（label=costusd），
        // row.get("costUsd") 得 null → String.valueOf(null)="null" → BigDecimal 抛 NumberFormatException
        // （RuntimeSnapshotRefresher 日志 quota resync failed: Character n is neither a decimal digit...）
        ClientEntity limited = client();
        limited.setQuotaSpec("[{\"name\":\"q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":5,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        when(clientDao.selectList(null)).thenReturn(List.of(limited));
        ModelDao modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of(model()));
        when(providerDao.selectList(null)).thenReturn(List.of(provider()));
        when(credentialDao.selectList(null)).thenReturn(List.of(credential()));
        RuntimeCatalogCache limitedCatalog = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        limitedCatalog.refresh();
        RouteDao routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute()));
        PublishedRouteCache routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        when(requestLogDao.selectMaps(any())).thenReturn(List.of(Map.of(
                "requestcount", 5L, "inputtokens", 0L, "outputtokens", 0L, "costusd", BigDecimal.ZERO)));
        InvocationPipeline resyncPipeline = new InvocationPipeline(limitedCatalog, routeCache,
                new BindingHealthRegistry(3, 10000), chatAdapter,
                new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, new CredentialCipher(null));

        resyncPipeline.resyncQuotas();

        assertThatThrownBy(() -> resyncPipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void rejectedRequestsDoNotConsumeRequestQuota() {
        // 配额记账时机：被选路等后续关口拒绝的请求不得消耗 REQUEST 配额
        // （limit=1 下连续两次 503 不得升级为 429）
        ClientEntity limited = client();
        limited.setQuotaSpec("[{\"name\":\"q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":1,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        InvocationPipeline p = pipelineWith(publishedRoute(true), model(), provider(), limited);
        markBindingUnhealthy();

        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()));
        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()));
    }

    @Test
    void admittedRequestsConsumeRequestQuota() throws IOException {
        // 行为锁定：通过全部关口的调用正常消耗 REQUEST 配额（limit=1：首次成功，第二次 429）
        ClientEntity limited = client();
        limited.setQuotaSpec("[{\"name\":\"q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":1,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        InvocationPipeline p = pipelineWith(publishedRoute(true), model(), provider(), limited);
        stubUpstreamOk();

        assertThat(p.invoke(PLAIN_KEY, chatBody(), INGRESS).httpStatus()).isEqualTo(200);
        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()));
    }

    @Test
    void upstreamClientErrorPassesThroughStatusAndBody() throws IOException {
        // 上游 4xx（接入方输入类错误）：状态与错误体透传，不包 502；落 ERROR 日志
        when(chatAdapter.chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new com.innospots.nexmux.inbound.protocol.UpstreamCallException(400,
                        "{\"error\":{\"message\":\"context too long\"}}"));

        InvocationPipeline.InvocationResult result = pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS);

        assertThat(result.httpStatus()).isEqualTo(400);
        assertThat(result.body()).contains("context too long");
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "ERROR".equals(log.getStatus()) && "HTTP_400".equals(log.getErrorCode())));
    }

    @Test
    void upstreamServerErrorStillMaps502() throws IOException {
        // 上游 5xx 维持归约：502 NMX060001
        when(chatAdapter.chatCompletions(anyString(), anyString(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new com.innospots.nexmux.inbound.protocol.UpstreamCallException(500,
                        "{\"error\":\"internal\"}"));

        assertThatThrownBy(() -> pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.UPSTREAM_FAILURE.fullCode()));
    }

    @Test
    void listModelsReturnsOnlyGrantedAliases() {
        // /v1/models：AppKey 鉴权 + 仅返回已授权的已发布别名（OpenAI 列表形状）
        String body = pipeline.listModels(PLAIN_KEY);

        assertThat(body).contains("\"object\":\"list\"")
                .contains("\"id\":\"general\"")
                .contains("\"owned_by\":\"nexmux\"");
        assertThatThrownBy(() -> pipeline.listModels("gw-sk-bad"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.APP_KEY_INVALID.fullCode()));
    }

    @Test
    void decryptsEncryptedCredentialForUpstream() throws IOException {
        // 凭证加密端到端：目录缓存中的密文凭证经管道解密后发给上游
        CredentialCipher cipher = new CredentialCipher("test-secret");
        RouteDao routeDao = Mockito.mock(RouteDao.class);
        when(routeDao.selectList(any())).thenReturn(List.of(publishedRoute()));
        PublishedRouteCache routeCache = new PublishedRouteCache(routeDao);
        routeCache.refresh();
        ModelDao modelDao = Mockito.mock(ModelDao.class);
        ProviderDao providerDao = Mockito.mock(ProviderDao.class);
        ProviderCredentialDao credentialDao = Mockito.mock(ProviderCredentialDao.class);
        ClientDao clientDao = Mockito.mock(ClientDao.class);
        when(modelDao.selectList(null)).thenReturn(List.of(model()));
        when(providerDao.selectList(null)).thenReturn(List.of(provider()));
        ProviderCredentialEntity encrypted = credential();
        encrypted.setCiphertext(cipher.encrypt("sk-upstream"));
        when(credentialDao.selectList(null)).thenReturn(List.of(encrypted));
        when(clientDao.selectList(null)).thenReturn(List.of(client()));
        RuntimeCatalogCache catalog = new RuntimeCatalogCache(modelDao, providerDao, credentialDao, clientDao);
        catalog.refresh();
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        when(settings.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        InvocationPipeline p = new InvocationPipeline(catalog, routeCache, healthRegistry,
                chatAdapter, new RequestLogWriter(requestLogDao, settings), requestLogDao, settings, cipher);
        stubUpstreamOk();

        p.invoke(PLAIN_KEY, chatBody(), INGRESS);

        Mockito.verify(chatAdapter).chatCompletions(anyString(),
                org.mockito.ArgumentMatchers.eq("sk-upstream"), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void candidateCostRateOverridesBindingMultiplierInCost() throws IOException {
        // G-01：候选行 C=3 覆盖 Binding 默认倍率，调用成本 ×3
        RouteEntity route = publishedRoute(true);
        route.setPublishedRouteSpec("{\"alias\":\"general\",\"ingress\":\"/v1/chat/completions\","
                + "\"strategyType\":\"FIXED\",\"candidates\":[{\"modelCode\":\"openai/o4-mini\","
                + "\"weight\":100,\"costRate\":3}],\"filterUnhealthyBindings\":true}");
        InvocationPipeline p = pipelineWith(route, model(), provider());
        stubUpstreamOk();

        p.invoke(PLAIN_KEY, chatBody(), INGRESS);

        // 基线成本 (10×10 + 5×30)/1e6 = 0.00025；C=3 覆盖后 = 0.00075
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getCostUsd() != null
                        && log.getCostUsd().compareTo(new BigDecimal("0.00075000")) == 0));
    }

    @Test
    void candidateWithoutRateInheritsBindingMultiplier() throws IOException {
        // G-01 对照：候选行留空 → 继承绑定默认（×1）
        InvocationPipeline p = pipelineWith(publishedRoute(true), model(), provider());
        stubUpstreamOk();

        p.invoke(PLAIN_KEY, chatBody(), INGRESS);

        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getCostUsd() != null
                        && log.getCostUsd().compareTo(new BigDecimal("0.00025000")) == 0));
    }

    @Test
    void disabledModelIsExcludedFromSelection() {
        // G-06：停用 Model 不再作为可用候选（即使已发布路由仍引用）→ 503 NMX020005
        ModelEntity disabled = model();
        disabled.setStatus("DISABLED");
        InvocationPipeline p = pipelineWith(publishedRoute(true), disabled, provider());

        assertThatThrownBy(() -> p.invoke(PLAIN_KEY, chatBody(), INGRESS))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()));
        Mockito.verifyNoInteractions(chatAdapter);
    }

    @Test
    void readTimeoutPrefersProviderThenPlatformSetting() throws IOException {
        // G-03/G-04：Provider.timeoutMs 优先；缺省回退服务设置 requestTimeoutMs=60000；
        // 连接超时取 upstreamConnectTimeoutMs=10000（PlatformSettingService.DEFAULTS）
        stubUpstreamOk();
        pipeline.invoke(PLAIN_KEY, chatBody(), INGRESS);
        Mockito.verify(chatAdapter).chatCompletions(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(5000),
                org.mockito.ArgumentMatchers.eq(10000), any());

        ProviderEntity noTimeout = provider();
        noTimeout.setTimeoutMs(null);
        InvocationPipeline p = pipelineWith(publishedRoute(true), model(), noTimeout);
        p.invoke(PLAIN_KEY, chatBody(), INGRESS);
        Mockito.verify(chatAdapter).chatCompletions(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(60000),
                org.mockito.ArgumentMatchers.eq(10000), any());
    }
}
