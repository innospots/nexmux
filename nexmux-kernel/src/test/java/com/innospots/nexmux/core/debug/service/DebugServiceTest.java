package com.innospots.nexmux.core.debug.service;

import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DebugServiceTest {

    private ModelDao modelDao;
    private ProviderDao providerDao;
    private ProviderCredentialDao providerCredentialDao;
    private ClientDao clientDao;
    private RouteDao routeDao;
    private ConfigAuditService configAuditService;
    private com.innospots.nexmux.core.ops.dao.RequestLogDao requestLogDao;
    private DebugService debugService;

    @BeforeEach
    void setUp() {
        modelDao = Mockito.mock(ModelDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        providerCredentialDao = Mockito.mock(ProviderCredentialDao.class);
        clientDao = Mockito.mock(ClientDao.class);
        routeDao = Mockito.mock(RouteDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        PlatformSettingService platformSettingService = Mockito.mock(PlatformSettingService.class);
        when(platformSettingService.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        requestLogDao = Mockito.mock(com.innospots.nexmux.core.ops.dao.RequestLogDao.class);
        debugService = new DebugService(
                modelDao, providerDao, providerCredentialDao, clientDao, routeDao, configAuditService,
                platformSettingService, requestLogDao,
                new RequestLogWriter(requestLogDao, platformSettingService),
                new com.innospots.nexmux.core.shared.security.CredentialCipher(null));
    }

    private ModelEntity model() {
        ModelEntity entity = new ModelEntity();
        entity.setModelId("md-1");
        entity.setModelCode("openai/o4-mini");
        entity.setPrimaryProviderId("pv-1");
        entity.setPrimaryProviderModelId("o4-mini");
        entity.setStatus("ENABLED");
        return entity;
    }

    private ProviderEntity provider() {
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderId("pv-1");
        entity.setProviderCode("openai");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setTimeoutMs(60000);
        entity.setStatus("ENABLED");
        return entity;
    }

    private ProviderCredentialEntity credential() {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setProviderId("pv-1");
        entity.setCiphertext("sk-test");
        entity.setKeyVersion(1);
        return entity;
    }

    private RouteEntity publishedRoute() {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("rt-1");
        entity.setAlias("general");
        entity.setStatus("PUBLISHED");
        entity.setPublishedRouteSpec(
                "{\"alias\":\"general\",\"strategyType\":\"FIXED\","
                        + "\"candidates\":[{\"modelCode\":\"openai/o4-mini\",\"weight\":100,\"enabled\":true}]}");
        return entity;
    }

    private ClientEntity client(String status, String routeIdsJson) {
        ClientEntity entity = new ClientEntity();
        entity.setClientId("cl-1");
        entity.setClientCode("mobile-app");
        entity.setStatus(status);
        entity.setAuthorizedRouteIds(routeIdsJson);
        return entity;
    }

    // ── modelInvoke 目标解析 ────────────────────────────────────

    @Test
    void modelInvokeRejectsUnknownModel() {
        when(modelDao.selectById("md-x")).thenReturn(null);

        assertThatThrownBy(() -> debugService.modelInvoke(
                new ModelInvokeDebugRequest("ghost/x", null, "你好", null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    @Test
    void modelInvokeReturnsFailureBodyWhenNoCredential() {
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        var result = debugService.modelInvoke(new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null));

        // 失败以结果体返回（不抛异常）：HTTP 422 + 可读错误
        assertThat(result.status()).isEqualTo(422);
        assertThat(result.body()).contains("credential");
    }

    // ── clientInvoke 前置校验 ───────────────────────────────────

    @Test
    void clientInvokeRejectsDisabledClient() {
        when(clientDao.selectOne(any())).thenReturn(client("DISABLED", "[\"rt-1\"]"));

        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好"));

        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body()).contains("disabled");
    }

    @Test
    void clientInvokeRejectsUngrantedRoute() {
        when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-other\"]"));
        when(routeDao.selectOne(any())).thenReturn(publishedRoute());

        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好"));

        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body()).contains("not granted");
    }

    @Test
    void clientInvokeRejectsUnpublishedRoute() {
        RouteEntity draft = publishedRoute();
        draft.setStatus("DRAFT");
        draft.setPublishedRouteSpec(null);
        when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(draft);

        assertThatThrownBy(() -> debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好")))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.ROUTE_NOT_PUBLISHED.fullCode()));
    }

    @Test
    void clientInvokeResolvesFirstEnabledCandidate() {
        when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(publishedRoute());
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        // 无凭证走失败结果体，但解析路径已验证（model 按候选 modelCode 被查询）
        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好"));

        assertThat(result.status()).isEqualTo(422);
        Mockito.verify(modelDao).selectOne(any());
    }

    // ── 流式调用（SSE 穿透）─────────────────────────────────

    /** 本地 SSE 上游：两个内容 chunk + usage chunk + [DONE]。 */
    private HttpServer sseUpstream(AtomicReference<String> upstreamBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            upstreamBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                    + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private void stubModelChain(HttpServer server) {
        when(modelDao.selectOne(any())).thenReturn(model());
        ProviderEntity upstream = provider();
        upstream.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        when(providerDao.selectById("pv-1")).thenReturn(upstream);
        when(providerCredentialDao.selectOne(any())).thenReturn(credential());
    }

    private static String writeToString(StreamingOutput stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        stream.write(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void modelInvokeStreamPipesChunksAndEmitsMeta() throws Exception {
        AtomicReference<String> upstreamBody = new AtomicReference<>();
        HttpServer server = sseUpstream(upstreamBody);
        try {
            stubModelChain(server);

            String sse = writeToString(debugService.modelInvokeStream(
                    new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null)));

            // 上游 chunk 逐行穿透；上游 [DONE] 被抑制，仅终态一个
            assertThat(sse).contains("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
            assertThat(sse).contains("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");
            assertThat(sse.split("data: \\[DONE\\]", -1)).hasSize(2);
            // 终态 meta：tokens / 延迟 / 请求与响应报文
            assertThat(sse).contains("event: debug-meta");
            assertThat(sse).contains("\"tokensIn\":3").contains("\"tokensOut\":2");
            assertThat(sse).contains("\"requestJson\"").contains("\"responseJson\"");
            assertThat(sse.trim()).endsWith("data: [DONE]");
            // 发往上游的请求强制 stream + include_usage
            assertThat(upstreamBody.get()).contains("\"stream\":true").contains("\"include_usage\":true");
            // 请求日志落库：聚合 reply 与 usage
            Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                    "SUCCESS".equals(log.getStatus())
                            && Long.valueOf(3).equals(log.getInputTokens())
                            && Long.valueOf(2).equals(log.getOutputTokens())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void modelInvokeStreamEmitsErrorEventWhenNoCredential() throws Exception {
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        String sse = writeToString(debugService.modelInvokeStream(
                new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null)));

        assertThat(sse).contains("event: debug-error").contains("\"status\":422");
        assertThat(sse).contains("credential");
        assertThat(sse.trim()).endsWith("data: [DONE]");
        // 与非流式一致：失败也落请求日志
        Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                "ERROR".equals(log.getStatus()) && "HTTP_422".equals(log.getErrorCode())));
    }

    @Test
    void modelInvokeStreamEmitsErrorEventWhenUpstreamUnreachable() throws Exception {
        when(modelDao.selectOne(any())).thenReturn(model());
        ProviderEntity unreachable = provider();
        unreachable.setBaseUrl("http://127.0.0.1:1");
        when(providerDao.selectById("pv-1")).thenReturn(unreachable);
        when(providerCredentialDao.selectOne(any())).thenReturn(credential());

        String sse = writeToString(debugService.modelInvokeStream(
                new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null)));

        assertThat(sse).contains("event: debug-error").contains("\"status\":502");
        assertThat(sse.trim()).endsWith("data: [DONE]");
    }

    @Test
    void clientInvokeStreamEmitsErrorForDisabledClient() throws Exception {
        when(clientDao.selectOne(any())).thenReturn(client("DISABLED", "[\"rt-1\"]"));

        String sse = writeToString(debugService.clientInvokeStream(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好")));

        assertThat(sse).contains("event: debug-error").contains("\"status\":403");
        assertThat(sse).contains("disabled");
        assertThat(sse.trim()).endsWith("data: [DONE]");
    }

    @Test
    void clientInvokeStreamPipesChunksAndEmitsRouteMeta() throws Exception {
        AtomicReference<String> upstreamBody = new AtomicReference<>();
        HttpServer server = sseUpstream(upstreamBody);
        try {
            when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-1\"]"));
            when(routeDao.selectOne(any())).thenReturn(publishedRoute());
            stubModelChain(server);

            String sse = writeToString(debugService.clientInvokeStream(
                    new ClientInvokeDebugRequest("cl-1", "general-chat", "你好")));

            assertThat(sse).contains("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
            assertThat(sse).contains("event: debug-meta");
            assertThat(sse).contains("\"routeGroup\":\"default\"");
            assertThat(sse).contains("\"quotaPreview\":\"未配置配额（不限量）\"");
            Mockito.verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                    "SUCCESS".equals(log.getStatus())
                            && "cl-1".equals(log.getClientId())
                            && "rt-1".equals(log.getRouteId())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientInvokeWritesErrorRequestLogWhenUpstreamUnreachable() {
        when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(publishedRoute());
        when(modelDao.selectOne(any())).thenReturn(model());
        ProviderEntity unreachable = provider();
        unreachable.setBaseUrl("http://127.0.0.1:1");
        when(providerDao.selectById("pv-1")).thenReturn(unreachable);
        when(providerCredentialDao.selectOne(any())).thenReturn(credential());

        var result = debugService.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好"));

        assertThat(result.status()).isEqualTo(502);
        Mockito.verify(requestLogDao).insert(Mockito.<com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity>argThat(log ->
                "ERROR".equals(log.getStatus())
                        && "openai/o4-mini".equals(log.getModelCode())
                        && "cl-1".equals(log.getClientId())
                        && "rt-1".equals(log.getRouteId())
                        && "HTTP_502".equals(log.getErrorCode())
                        && result.requestId().equals(log.getRequestId())));
    }

    // ── 调试限域（ADR-0001 审计 A2：可关闭 + 日配额）────────────────

    private PlatformSettingService settingsWith(String key, String value) {
        PlatformSettingService settings = Mockito.mock(PlatformSettingService.class);
        Map<String, String> values = new java.util.LinkedHashMap<>(PlatformSettingService.DEFAULTS);
        values.put(key, value);
        when(settings.getAll()).thenReturn(values);
        return settings;
    }

    private DebugService serviceWithSettings(PlatformSettingService settings) {
        return new DebugService(modelDao, providerDao, providerCredentialDao, clientDao, routeDao,
                configAuditService, settings, requestLogDao,
                new RequestLogWriter(requestLogDao, settings),
                new com.innospots.nexmux.core.shared.security.CredentialCipher(null));
    }

    @Test
    void modelInvokeRejectedWhenDebugDisabled() {
        DebugService service = serviceWithSettings(settingsWith("debugInvokeEnabled", "false"));

        assertThatThrownBy(() -> service.modelInvoke(
                new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null)))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.DEBUG_DISABLED.fullCode()));
    }

    @Test
    void clientInvokeRejectedWhenDebugDisabled() {
        DebugService service = serviceWithSettings(settingsWith("debugInvokeEnabled", "false"));

        assertThatThrownBy(() -> service.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好")))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.DEBUG_DISABLED.fullCode()));
    }

    @Test
    void clientInvokeRejectedWhenDailyQuotaExhausted() {
        DebugService service = serviceWithSettings(settingsWith("debugDailyQuota", "200"));
        when(requestLogDao.selectCount(any())).thenReturn(200L);

        assertThatThrownBy(() -> service.clientInvoke(
                new ClientInvokeDebugRequest("cl-1", "general-chat", "你好")))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.DEBUG_QUOTA_EXCEEDED.fullCode()));
    }

    @Test
    void negativeQuotaMeansUnlimited() {
        DebugService service = serviceWithSettings(settingsWith("debugDailyQuota", "-1"));
        when(requestLogDao.selectCount(any())).thenReturn(99999L);
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        // 不限量：不因配额拒绝，走到凭证缺失的业务失败
        var result = service.modelInvoke(new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null));

        assertThat(result.status()).isEqualTo(422);
    }

    // ── 共享选路（ADR-0001 审计 B1：与 Runtime 同一 CandidateSelector）──────────

    @Test
    void clientInvokeAppliesStrategyAndSkipsDisabledCandidate() {
        RouteEntity route = publishedRoute();
        // WEIGHTED：首候选停用 + 次候选权重 100，旧实现 findFirst 会命中停用候选
        route.setPublishedRouteSpec(
                "{\"alias\":\"general\",\"strategyType\":\"WEIGHTED\","
                        + "\"candidates\":[{\"modelCode\":\"m-disabled\",\"weight\":100,\"enabled\":false},"
                        + "{\"modelCode\":\"openai/o4-mini\",\"weight\":100,\"enabled\":true}]}");
        when(clientDao.selectOne(any())).thenReturn(client("ENABLED", "[\"rt-1\"]"));
        when(routeDao.selectOne(any())).thenReturn(route);
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        var result = debugService.clientInvoke(new ClientInvokeDebugRequest("cl-1", "general-chat", "你好"));

        // 选中启用的 openai/o4-mini（无凭证 422），routeTrace 记录选择上下文
        assertThat(result.status()).isEqualTo(422);
        Mockito.verify(requestLogDao).insert(Mockito.<com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity>argThat(log ->
                log.getRouteTrace() != null
                        && log.getRouteTrace().contains("\"strategy\":\"WEIGHTED\"")
                        && log.getRouteTrace().contains("\"candidateModelCode\":\"openai/o4-mini\"")
                        && log.getRouteTrace().contains("\"poolSize\":1")));
    }

    @Test
    void debugRequestLogIsMarkedAsDebugSource() {
        when(modelDao.selectOne(any())).thenReturn(model());
        when(providerDao.selectById("pv-1")).thenReturn(provider());
        when(providerCredentialDao.selectOne(any())).thenReturn(null);

        debugService.modelInvoke(new ModelInvokeDebugRequest("openai/o4-mini", null, "你好", null));

        // ADR-0001 审计 A1：Console 调试写入显式标记 source=DEBUG，与 Runtime 流量区分
        Mockito.verify(requestLogDao).insert(Mockito.<com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity>argThat(log ->
                "DEBUG".equals(log.getSource())));
    }
}
