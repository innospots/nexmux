package com.innospots.nexmux.inbound.invocation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.security.CredentialCipher;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeRouter;
import com.innospots.nexmux.inbound.invocation.adapter.UpstreamCallException;
import com.innospots.nexmux.inbound.invocation.adapter.UpstreamChatAdapter;
import com.innospots.nexmux.inbound.invocation.domain.model.InvocationContext;
import com.innospots.nexmux.inbound.invocation.domain.model.InvocationResult;
import com.innospots.nexus.base.exception.NexusException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;

/**
 * 调用流水线：鉴权 → 路由授权 → 配额 → 限流 → 选路 → 绑定 → 上游转发 → 计量落库。
 *
 * <p>本类只做编排：解析委托 {@link InvocationContextResolver}，上游请求体由
 * {@link UpstreamRequestBuilder} 组装，超时由 {@link UpstreamTimeoutResolver} 解析，
 * 记账与日志由 {@link InvocationUsageRecorder} 承担。</p>
 *
 * <p>P0 语义：选中的绑定调用失败后直接结束请求，不自动切换。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see InvocationContextResolver
 * @see InvocationUsageRecorder
 */
@RequiredArgsConstructor
public final class InvocationPipeline {

    private static final String CHAT_COMPLETIONS = "/chat/completions";

    private final RuntimeCatalogCache runtimeCatalogCache;
    private final RuntimeRouter runtimeRouter;
    private final AppKeyVerifier appKeyVerifier;
    private final QuotaGate quotaGate;
    private final RateLimitGate rateLimitGate;
    private final ModelBindingResolver bindingResolver;
    private final UpstreamChatAdapter chatAdapter;
    private final UpstreamTimeoutResolver timeoutResolver;
    private final InvocationUsageRecorder usageRecorder;
    private final CredentialCipher credentialCipher;

    /**
     * 装配流水线（运行模块用）。
     *
     * @param runtimeCatalogCache  配置目录缓存
     * @param publishedRouteCache  已发布路由缓存
     * @param bindingHealthRegistry 绑定健康状态机
     * @param chatAdapter          上游协议适配器
     * @param requestLogWriter     调用日志写入器
     * @param requestLogDao        调用日志 DAO（配额校准聚合）
     * @param platformSettingService 平台设置（超时配置来源）
     * @param credentialCipher     上游凭证解密器
     * @return 流水线实例
     */
    public static InvocationPipeline of(RuntimeCatalogCache runtimeCatalogCache,
                                        PublishedRouteCache publishedRouteCache,
                                        com.innospots.nexmux.core.health.service.BindingHealthRegistry
                                                bindingHealthRegistry,
                                        UpstreamChatAdapter chatAdapter,
                                        RequestLogWriter requestLogWriter,
                                        RequestLogDao requestLogDao,
                                        PlatformSettingService platformSettingService,
                                        CredentialCipher credentialCipher) {
        RuntimeRouter router = new RuntimeRouter(publishedRouteCache);
        QuotaGate quotaGate = new QuotaGate();
        RateLimitGate rateLimitGate = new RateLimitGate();
        return new InvocationPipeline(
                runtimeCatalogCache,
                router,
                new AppKeyVerifier(runtimeCatalogCache),
                quotaGate,
                rateLimitGate,
                new ModelBindingResolver(bindingHealthRegistry),
                chatAdapter,
                new UpstreamTimeoutResolver(platformSettingService),
                new InvocationUsageRecorder(quotaGate, rateLimitGate, requestLogWriter, requestLogDao),
                credentialCipher);
    }

    /**
     * 非流式 Chat Completions 调用。
     *
     * @param presentedKey AppKey 明文
     * @param requestBody  请求体
     * @param ingressPath  入口路径
     * @return 调用结果
     */
    public InvocationResult invoke(String presentedKey, Map<String, Object> requestBody,
                                   String ingressPath) {
        return invokeOperation(presentedKey, requestBody, ingressPath, CHAT_COMPLETIONS);
    }

    /**
     * 非流式 OpenAI 兼容操作调用，完整复用鉴权、治理与计量链。
     *
     * @param presentedKey      AppKey 明文
     * @param requestBody       请求体
     * @param ingressPath       入口路径
     * @param upstreamOperation 上游操作路径（如 {@code /responses}）
     * @return 调用结果
     */
    public InvocationResult invokeOperation(String presentedKey, Map<String, Object> requestBody,
                                            String ingressPath, String upstreamOperation) {
        InvocationContext context = resolver().resolve(presentedKey, requestBody, ingressPath);
        requireSupportedCapabilities(context, requestBody);
        List<Semaphore> permits = rateLimitGate.check(context.client(), context.route().routeId());
        quotaGate.recordRequest(context.client());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> upstreamBody =
                    UpstreamRequestBuilder.build(requestBody, context.model(), context.binding(), false);
            String requestJson = NexmuxJson.write(upstreamBody);
            String body = CHAT_COMPLETIONS.equals(upstreamOperation)
                    ? chatAdapter.chatCompletions(
                            context.provider().getBaseUrl(), credentialOf(context), requestJson,
                            timeoutResolver.readTimeoutMillis(context.provider()),
                            timeoutResolver.connectTimeoutMillis(), context.provider().getProxy())
                    : chatAdapter.post(upstreamOperation,
                            context.provider().getBaseUrl(), credentialOf(context), requestJson,
                            timeoutResolver.readTimeoutMillis(context.provider()),
                            timeoutResolver.connectTimeoutMillis(), context.provider().getProxy());
            long latency = System.currentTimeMillis() - start;
            JsonNode usage = readJsonTolerant(body);
            JsonNode usageNode = usage == null ? null : usage.get("usage");
            InvocationResult result = new InvocationResult(200, body, latency, latency,
                    longOrNull(usageNode, "prompt_tokens"),
                    longOrNull(usageNode, "completion_tokens"), context);
            usageRecorder.settle(context, result, requestBody);
            return result;
        } catch (UpstreamCallException e) {
            if (e.isClientError()) {
                // 上游 4xx = 接入方输入类错误：透传上游状态与错误体（不归约 502），落 ERROR 日志
                InvocationResult failure = new InvocationResult(e.status(), e.responseBody(),
                        System.currentTimeMillis() - start, -1, null, null, context);
                usageRecorder.settle(context, failure, requestBody);
                return failure;
            }
            throw upstreamFailure(context, requestBody, start, e);
        } catch (IOException e) {
            throw upstreamFailure(context, requestBody, start, e);
        } finally {
            rateLimitGate.releaseConcurrent(permits);
        }
    }

    /**
     * 流式调用（SSE）：上游 chunk 原样透传至 {@code out}。
     *
     * <p>上游 4xx 在响应状态行已提交后以带内事件透传；5xx/IO 以
     * {@link NexusException} 抛出，由入口端决定错误形态。</p>
     *
     * @param presentedKey AppKey 明文
     * @param requestBody  请求体
     * @param ingressPath  入口路径
     * @param out          下游输出流
     * @return 调用结果
     * @throws IOException 写出失败
     */
    public InvocationResult invokeStream(String presentedKey, Map<String, Object> requestBody,
                                         String ingressPath, OutputStream out) throws IOException {
        InvocationContext context = resolver().resolve(presentedKey, requestBody, ingressPath);
        requireSupportedCapabilities(context, requestBody);
        List<Semaphore> permits = rateLimitGate.check(context.client(), context.route().routeId());
        quotaGate.recordRequest(context.client());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> upstreamBody =
                    UpstreamRequestBuilder.build(requestBody, context.model(), context.binding(), true);
            UpstreamChatAdapter.StreamOutcome outcome = chatAdapter.streamChatCompletions(
                    context.provider().getBaseUrl(), credentialOf(context),
                    NexmuxJson.write(upstreamBody),
                    timeoutResolver.readTimeoutMillis(context.provider()),
                    timeoutResolver.connectTimeoutMillis(), context.provider().getProxy(), out);
            InvocationResult result = new InvocationResult(200, outcome.synthesizedBody(),
                    System.currentTimeMillis() - start, outcome.ttftMs(),
                    outcome.tokensIn(), outcome.tokensOut(), context);
            usageRecorder.settle(context, result, requestBody);
            return result;
        } catch (UpstreamCallException e) {
            if (e.isClientError()) {
                // 上游 4xx：记账后原样抛给入口端（状态行已提交 200，错误体以带内事件透传）
                InvocationResult failure = new InvocationResult(e.status(), e.responseBody(),
                        System.currentTimeMillis() - start, -1, null, null, context);
                usageRecorder.settle(context, failure, requestBody);
                throw e;
            }
            throw upstreamFailure(context, requestBody, start, e);
        } catch (IOException e) {
            throw upstreamFailure(context, requestBody, start, e);
        } finally {
            rateLimitGate.releaseConcurrent(permits);
        }
    }

    /**
     * 模型发现（{@code GET /v1/models}）：返回该客户端已授权且已发布的别名清单。
     *
     * @param presentedKey AppKey 明文
     * @return OpenAI 列表形状的 JSON 文本
     */
    public String listModels(String presentedKey) {
        ClientEntity client = appKeyVerifier.authenticate(presentedKey);
        Set<String> granted =
                new HashSet<>(NexmuxJson.readStringList(client.getAuthorizedRouteIds()));
        List<Map<String, Object>> data = runtimeRouter.routeCache().routes().stream()
                .filter(route -> route.spec().alias() != null && granted.contains(route.routeId()))
                .map(route -> Map.<String, Object>of(
                        "id", route.spec().alias(), "object", "model", "owned_by", "nexmux"))
                .sorted(java.util.Comparator.comparing(item -> (String) item.get("id")))
                .toList();
        return NexmuxJson.write(Map.of("object", "list", "data", data));
    }

    /**
     * 以运行库聚合回填配额计数（进程重启 / 周期校准）。
     */
    public void resyncQuotas() {
        usageRecorder.resyncQuotas(runtimeCatalogCache.clients());
    }

    /**
     * 配额关口（供运维诊断与校准使用）。
     *
     * @return 配额关口实例
     */
    public QuotaGate quotaGate() {
        return quotaGate;
    }

    private InvocationContextResolver resolver() {
        return new InvocationContextResolver(
                runtimeCatalogCache, runtimeRouter, appKeyVerifier, quotaGate, bindingResolver);
    }

    /** 请求级能力校验在限流和上游调用之前执行，避免不支持的能力被静默透传。 */
    private static void requireSupportedCapabilities(InvocationContext context,
                                                     Map<String, Object> requestBody) {
        Object tools = requestBody.get("tools");
        boolean toolsRequested = tools instanceof List<?> list && !list.isEmpty();
        if (!toolsRequested) {
            return;
        }
        boolean supported = NexmuxJson.readStringList(context.model().getCapabilityTags()).stream()
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(value -> value.equals("tool calling") || value.equals("function calling")
                        || value.equals("tools") || value.equals("function call"));
        if (!supported) {
            throw NexusException.build(NexmuxStatusCode.INVALID_REQUEST,
                    "model does not support function calling");
        }
    }

    /** 上游故障（5xx / IO）：落 ERROR 日志并归约 502。 */
    private NexusException upstreamFailure(InvocationContext context, Map<String, Object> requestBody,
                                           long start, IOException e) {
        InvocationResult failure = new InvocationResult(502,
                "{\"error\":\"upstream call failed: " + abbreviate(e.getMessage()) + "\"}",
                System.currentTimeMillis() - start, -1, null, null, context);
        usageRecorder.settle(context, failure, requestBody);
        return NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                "upstream call failed: " + abbreviate(e.getMessage()));
    }

    private String credentialOf(InvocationContext context) {
        return runtimeCatalogCache.findCredential(context.binding().providerId())
                .map(ProviderCredentialEntity::getCiphertext)
                .filter(ciphertext -> ciphertext != null && !ciphertext.isBlank())
                .map(credentialCipher::decrypt)
                .map(String::trim)
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                        "provider credential not configured: " + context.binding().providerId()));
    }

    private static JsonNode readJsonTolerant(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
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
