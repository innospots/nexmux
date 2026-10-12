package com.innospots.nexmux.inbound.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.domain.selection.CandidateSelector;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.protocol.UpstreamChatAdapter;
import com.innospots.nexmux.inbound.runtime.BindingHealthRegistry;
import com.innospots.nexmux.inbound.runtime.PublishedRouteCache;
import com.innospots.nexmux.inbound.runtime.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.runtime.RuntimeRouter;
import com.innospots.nexmux.inbound.pipeline.ModelBindingSelector.ResolvedBinding;
import com.innospots.nexus.base.exception.NexusException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime 调用流水线（PRD §31，nexmux-runtime-design §7.3）：
 * 鉴权 → 路由授权 → 配额 → 限流 → 路由解析（已发布快照）→ 候选选择（共享
 * {@link CandidateSelector}）→ Model 内 Binding 选择（健康过滤 + 权重）→ 上游转发
 * （OpenAI 兼容，非流式 / SSE）→ 计量落库（{@code source=RUNTIME}）。
 *
 * <p>P0 选中 Binding 调用失败后结束请求，不自动切换（PRD §19.7）。
 */
public final class InvocationPipeline {

    /** Runtime 写入 {@code mux_request_log.source} 的标记。 */
    public static final String REQUEST_LOG_SOURCE = "RUNTIME";

    private static final int DEFAULT_UPSTREAM_TIMEOUT_MS = 30000;

    private final RuntimeCatalogCache catalogCache;
    private final RuntimeRouter router;
    private final AppKeyVerifier appKeyVerifier;
    private final QuotaGate quotaGate;
    private final RateLimitGate rateLimitGate;
    private final ModelBindingSelector bindingSelector;
    private final UpstreamChatAdapter chatAdapter;
    private final RequestLogWriter requestLogWriter;
    private final RequestLogDao requestLogDao;

    public InvocationPipeline(RuntimeCatalogCache catalogCache, PublishedRouteCache routeCache,
                              BindingHealthRegistry healthRegistry,
                              UpstreamChatAdapter chatAdapter, RequestLogWriter requestLogWriter,
                              RequestLogDao requestLogDao) {
        this.catalogCache = catalogCache;
        this.router = new RuntimeRouter(routeCache);
        this.appKeyVerifier = new AppKeyVerifier(catalogCache);
        this.quotaGate = new QuotaGate();
        this.rateLimitGate = new RateLimitGate();
        this.bindingSelector = new ModelBindingSelector(healthRegistry);
        this.chatAdapter = chatAdapter;
        this.requestLogWriter = requestLogWriter;
        this.requestLogDao = requestLogDao;
    }

    /**
     * 以运行库聚合回填配额计数（进程重启 / 周期校准，nexmux-runtime-design §9）：
     * 对配置了配额规则的客户端，按当前生效窗口聚合 {@code mux_request_log}
     * （仅 {@code source=RUNTIME}），差额并入内存计数器。
     */
    public void resyncQuotas() {
        for (ClientEntity client : catalogCache.clients()) {
            List<QuotaGate.QuotaRuleSpec> rules = QuotaGate.enabledRules(client);
            if (rules.isEmpty()) {
                continue;
            }
            LocalDateTime earliest = rules.stream()
                    .map(QuotaGate::windowStart)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
            // 聚合别名须加双引号保留大小写（同 StatsQueryDao 约定）：
            // H2 DATABASE_TO_LOWER=TRUE 会把未加引号的别名小写化（label=costusd），导致驼峰键取不到值
            List<Map<String, Object>> rows = requestLogDao.selectMaps(new QueryWrapper<RequestLogEntity>()
                    .select("COUNT(*) AS \"requestCount\"",
                            "COALESCE(SUM(input_tokens),0) AS \"inputTokens\"",
                            "COALESCE(SUM(output_tokens),0) AS \"outputTokens\"",
                            "COALESCE(SUM(cost_usd),0) AS \"costUsd\"")
                    .eq("client_id", client.getClientId())
                    .eq("source", REQUEST_LOG_SOURCE)
                    .ge("requested_at", earliest));
            if (rows.isEmpty()) {
                continue;
            }
            Map<String, Object> row = rows.get(0);
            long in = numberOf(valueOf(row, "inputTokens"));
            long out = numberOf(valueOf(row, "outputTokens"));
            Map<String, Long> usage = Map.of(
                    "REQUEST", numberOf(valueOf(row, "requestCount")),
                    "INPUT_TOKEN", in,
                    "OUTPUT_TOKEN", out,
                    "TOTAL_TOKEN", in + out,
                    // COST 计数刻度与 QuotaGate 一致：1e-8 美元
                    "COST", scaledCostOf(valueOf(row, "costUsd")));
            quotaGate.resync(client.getClientId(), rules, usage);
        }
    }

    /** 取聚合行值：优先原键，回退小写键（防御未加引号别名被 H2 小写化的历史库/方言差异）。 */
    private static Object valueOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toLowerCase(java.util.Locale.ROOT));
    }

    /** COST 聚合计值换算（1e-8 美元刻度），null 安全（与 {@link #numberOf} 一致）。 */
    private static long scaledCostOf(Object value) {
        return value == null ? 0 : new java.math.BigDecimal(String.valueOf(value)).movePointRight(8).longValue();
    }

    private static long numberOf(Object value) {
        return value == null ? 0 : new java.math.BigDecimal(String.valueOf(value)).longValue();
    }

    /** 供配额校准（RuntimeSnapshotRefresher 周期调用）。 */
    public QuotaGate quotaGate() {
        return quotaGate;
    }

    /** 一次调用的解析上下文（计量与 routeTrace 用）。 */
    public record InvocationContext(String requestId, String traceId, ClientEntity client,
                                    PublishedRouteCache.PublishedRoute route,
                                    CandidateSelector.Selection selection,
                                    ModelEntity model, ResolvedBinding binding,
                                    ProviderEntity provider,
                                    List<String> softQuotaTriggered) {
    }

    /** 调用结果。 */
    public record InvocationResult(int httpStatus, String body, long latencyMs, long ttftMs,
                                   Long tokensIn, Long tokensOut, InvocationContext context) {
    }

    /** 非流式调用。 */
    public InvocationResult invoke(String presentedKey, Map<String, Object> requestBody,
                                   String ingressPath) {
        InvocationContext context = resolve(presentedKey, requestBody, ingressPath);
        List<String> concurrentKeys = rateLimitGate.check(context.client(), context.route().routeId());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> upstreamBody = upstreamBody(requestBody, context.binding(), false);
            String body = chatAdapter.chatCompletions(
                    context.provider().getBaseUrl(), credentialOf(context), NexmuxJson.write(upstreamBody),
                    timeoutOf(context.provider()));
            long latency = System.currentTimeMillis() - start;
            JsonNode parsed = readJsonTolerant(body);
            JsonNode usage = parsed == null ? null : parsed.get("usage");
            InvocationResult result = new InvocationResult(200, body, latency, latency,
                    longOrNull(usage, "prompt_tokens"), longOrNull(usage, "completion_tokens"), context);
            settle(context, result, requestBody);
            return result;
        } catch (IOException e) {
            InvocationResult failure = new InvocationResult(502,
                    "{\"error\":\"upstream call failed: " + abbreviate(e.getMessage()) + "\"}",
                    System.currentTimeMillis() - start, -1, null, null, context);
            settle(context, failure, requestBody);
            throw NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                    "upstream call failed: " + abbreviate(e.getMessage()));
        } finally {
            rateLimitGate.releaseConcurrent(context.client(), concurrentKeys);
        }
    }

    /**
     * 流式调用（SSE）：上游 chunk 原样透传至 {@code out}；上游失败在写出字节前抛
     * {@code NexusException(UPSTREAM_FAILURE)}，由 ingress 决定错误形态。
     */
    public InvocationResult invokeStream(String presentedKey, Map<String, Object> requestBody,
                                         String ingressPath, OutputStream out) {
        InvocationContext context = resolve(presentedKey, requestBody, ingressPath);
        List<String> concurrentKeys = rateLimitGate.check(context.client(), context.route().routeId());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> upstreamBody = upstreamBody(requestBody, context.binding(), true);
            UpstreamChatAdapter.StreamOutcome outcome = chatAdapter.streamChatCompletions(
                    context.provider().getBaseUrl(), credentialOf(context), NexmuxJson.write(upstreamBody),
                    timeoutOf(context.provider()), out);
            InvocationResult result = new InvocationResult(200, outcome.synthesizedBody(),
                    System.currentTimeMillis() - start, outcome.ttftMs(),
                    outcome.tokensIn(), outcome.tokensOut(), context);
            settle(context, result, requestBody);
            return result;
        } catch (IOException e) {
            InvocationResult failure = new InvocationResult(502,
                    "{\"error\":\"upstream call failed: " + abbreviate(e.getMessage()) + "\"}",
                    System.currentTimeMillis() - start, -1, null, null, context);
            settle(context, failure, requestBody);
            throw NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                    "upstream call failed: " + abbreviate(e.getMessage()));
        } finally {
            rateLimitGate.releaseConcurrent(context.client(), concurrentKeys);
        }
    }

    /** 解析链路：鉴权 → 路由 → 授权 → 配额 → 健康预过滤 → 候选 → Binding → 供应商。 */
    private InvocationContext resolve(String presentedKey, Map<String, Object> requestBody,
                                      String ingressPath) {
        ClientEntity client = appKeyVerifier.authenticate(presentedKey);
        String alias = textValue(requestBody.get("model"));
        if (alias == null) {
            throw NexusException.build(NexmuxStatusCode.INVALID_REQUEST, "model (route alias) is required");
        }
        PublishedRouteCache.PublishedRoute route = routerCache().findByAlias(alias)
                .filter(r -> r.spec().ingress() == null || r.spec().ingress().isBlank()
                        || r.spec().ingress().equals(ingressPath))
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.ROUTE_NOT_FOUND,
                        "no published route for alias: " + alias));
        if (!NexmuxJson.readStringList(client.getAuthorizedRouteIds()).contains(route.routeId())) {
            throw NexusException.build(NexmuxStatusCode.ROUTE_ACCESS_DENIED,
                    "route is not granted to this client");
        }
        // 先检查后记账：limit=N 允许 N 次成功调用（检查不含在途请求）
        List<String> softQuota = quotaGate.check(client);
        quotaGate.recordRequest(client);

        RouteSpec spec = route.spec();
        Set<String> unhealthy = unhealthyModelCodes(spec);
        String sessionKey = textValue(requestBody.get("user")) != null
                ? textValue(requestBody.get("user")) : client.getClientCode();
        CandidateSelector.Selection selection = router.resolveByAlias(alias, unhealthy, sessionKey)
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                        "no available candidate in route: " + alias));
        ModelEntity model = catalogCache.findModel(selection.candidate().modelCode())
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                        "candidate model not found: " + selection.candidate().modelCode()));
        // Binding 级健康过滤跟随路由快照开关（不过滤时选中不健康绑定将真实调上游并失败）
        ResolvedBinding binding = bindingSelector.select(model, spec.filterUnhealthyBindings())
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                        "no healthy binding for model: " + model.getModelCode()));
        ProviderEntity provider = catalogCache.findProvider(binding.providerId())
                .filter(p -> "ENABLED".equals(p.getStatus()))
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                        "provider not available: " + binding.providerId()));

        return new InvocationContext(newRequestId(), UUID.randomUUID().toString().replace("-", ""),
                client, route, selection, model, binding, provider, softQuota);
    }

    private PublishedRouteCache routerCache() {
        return router.routeCache();
    }

    /** 路由级健康预过滤（spec 开启时）：无可用 Binding 的候选模型进入不健康集合。 */
    private Set<String> unhealthyModelCodes(RouteSpec spec) {
        if (!spec.filterUnhealthyBindings()) {
            return Set.of();
        }
        Set<String> unhealthy = new HashSet<>();
        spec.candidates().forEach(candidate -> {
            if (candidate.enabled() != null && !candidate.enabled()) {
                return;
            }
            boolean selectable = catalogCache.findModel(candidate.modelCode())
                    .map(bindingSelector::hasSelectableBinding)
                    .orElse(false);
            if (!selectable) {
                unhealthy.add(candidate.modelCode());
            }
        });
        return unhealthy;
    }

    /** 上游请求体：透传客户端参数，model 替换为绑定上游 ID，按需置 stream。 */
    private static Map<String, Object> upstreamBody(Map<String, Object> requestBody,
                                                    ResolvedBinding binding, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>(requestBody);
        body.put("model", binding.providerModelId());
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        } else {
            body.remove("stream_options");
        }
        return body;
    }

    private String credentialOf(InvocationContext context) {
        return catalogCache.findCredential(context.binding().providerId())
                .map(ProviderCredentialEntity::getCiphertext)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                        "provider credential not configured: " + context.binding().providerId()));
    }

    private static int timeoutOf(ProviderEntity provider) {
        return provider.getTimeoutMs() == null || provider.getTimeoutMs() <= 0
                ? DEFAULT_UPSTREAM_TIMEOUT_MS : provider.getTimeoutMs();
    }

    /** 收尾：记账（配额 tokens / 限流 TPM）+ 计量落库（成功与失败都落）。 */
    private void settle(InvocationContext context, InvocationResult result, Map<String, Object> requestBody) {
        long tokensIn = result.tokensIn() == null ? 0 : result.tokensIn();
        long tokensOut = result.tokensOut() == null ? 0 : result.tokensOut();
        if (result.httpStatus() == 200) {
            quotaGate.recordUsage(context.client(), tokensIn, tokensOut, null);
            rateLimitGate.recordTokens(context.client(), context.route().routeId(), tokensIn + tokensOut);
        }
        writeRequestLog(context, result, requestBody);
    }

    private void writeRequestLog(InvocationContext context, InvocationResult result,
                                 Map<String, Object> requestBody) {
        ClientEntity client = context.client();
        ProviderEntity provider = context.provider();
        RouteSpec spec = context.route().spec();
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(context.requestId());
        entity.setTraceId(context.traceId());
        entity.setSource(REQUEST_LOG_SOURCE);
        entity.setClientId(client.getClientId());
        entity.setRouteId(context.route().routeId());
        entity.setModelCode(context.model().getModelCode());
        entity.setStatus(result.httpStatus() == 200 ? "SUCCESS" : "ERROR");
        entity.setLatencyMs(result.latencyMs());
        entity.setTtftMs(result.ttftMs() < 0 ? null : result.ttftMs());
        entity.setErrorCode(result.httpStatus() == 200 ? null : "HTTP_" + result.httpStatus());
        entity.setErrorType(result.httpStatus() == 200 ? null : "UPSTREAM");
        entity.setRequestedAt(LocalDateTime.now());
        entity.setAppKeyId(ClientService.appKeyIdOf(client));
        entity.setProviderCode(provider.getProviderCode());
        entity.setProtocol(context.binding().upstreamProtocol() != null
                ? context.binding().upstreamProtocol() : provider.getDefaultProtocol());
        entity.setRouteGroup(spec.tags() == null || spec.tags().isEmpty() ? "default" : spec.tags().get(0));
        entity.setApplication(client.getDisplayName());
        entity.setAlias(spec.alias());
        entity.setHttpStatus(result.httpStatus());
        entity.setUpstreamModel(context.binding().providerModelId());
        entity.setUpstreamEndpoint(provider.getBaseUrl());
        entity.setUpstreamStatus(String.valueOf(result.httpStatus()));
        entity.setUpstreamLatencyMs(result.latencyMs());
        entity.setRetries(0);
        entity.setIngressMethod("POST");
        entity.setIngressPath(spec.ingress());
        entity.setRouteTrace(routeTrace(context));
        requestLogWriter.write(entity, context.model().getPricingSpec(),
                context.binding().costMultiplier(), context.binding().referenceMultiplier(),
                NexmuxJson.write(requestBody), result.body());
        // 成本回填配额（COST 指标）
        if (result.httpStatus() == 200 && entity.getCostUsd() != null) {
            quotaGate.recordUsage(client, 0, 0, entity.getCostUsd());
        }
    }

    /** routeTrace：选路 / Binding / 配额 / 限流上下文全量留存（PRD §36）。 */
    private static String routeTrace(InvocationContext context) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("debug", false);
        trace.put("strategy", context.selection().strategyType());
        trace.put("candidateModelCode", context.selection().candidate().modelCode());
        trace.put("poolSize", context.selection().poolSize());
        trace.put("healthFilterApplied", context.selection().healthFilterApplied());
        trace.put("stickyApplied", context.selection().stickyApplied());
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("providerId", context.binding().providerId());
        binding.put("providerModelId", context.binding().providerModelId());
        binding.put("primary", context.binding().primary());
        trace.put("binding", binding);
        if (!context.softQuotaTriggered().isEmpty()) {
            trace.put("softQuotaTriggered", context.softQuotaTriggered());
        }
        return NexmuxJson.write(trace);
    }

    private static String newRequestId() {
        return "rq-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static JsonNode readJsonTolerant(String json) {
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isNumber()
                ? node.get(field).longValue() : null;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "unknown";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
