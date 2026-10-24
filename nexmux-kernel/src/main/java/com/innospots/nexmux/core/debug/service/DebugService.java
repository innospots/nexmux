package com.innospots.nexmux.core.debug.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.service.ClientService;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.debug.domain.request.ClientInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.vo.DebugInvokeResultVo;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.selection.CandidateSelector;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 调试台真实穿透调用（设计 §4.3：模型调试直连 Binding，客户端调试模拟全链路）。
 *
 * <p>口径（ADR-0001 审计 A1/A2/A3/B1 修复）：调试台属于 Console 内的「数据面模拟」，
 * 受服务设置限域——{@code debugInvokeEnabled} 可整体关闭、{@code debugDailyQuota} 限制日调用量，
 * 每次调用写配置审计；落 {@code mux_request_log} 的调试记录显式标记 {@code source=DEBUG}，
 * 与 Runtime 真实流量区分。选路经 {@link CandidateSelector}（与未来 Runtime 同一实现），
 * 策略 / 权重 / 优先级 / 粘性会话在调试链路可观测地生效。
 *
 * <p>失败以结果体返回（httpStatus + 可读 body），不抛业务异常；
 * 目标不存在 / 路由未发布等契约错误仍走 NexusException。
 */
@RequiredArgsConstructor
public class DebugService {

    /** 上游超时兜底（服务设置 upstreamConnectTimeoutMs 缺失 / 非法时）。 */
    private static final int UPSTREAM_TIMEOUT_MS = 30000;

    /** 调试写入 {@code mux_request_log.source} 的标记（与 Runtime 真实流量区分）。 */
    static final String REQUEST_LOG_SOURCE = "DEBUG";

    private final ModelDao modelDao;
    private final ProviderDao providerDao;
    private final ProviderCredentialDao providerCredentialDao;
    private final ClientDao clientDao;
    private final RouteDao routeDao;
    private final ConfigAuditService configAuditService;
    private final PlatformSettingService platformSettingService;
    private final RequestLogDao requestLogDao;
    private final RequestLogWriter requestLogWriter;
    private final com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher;

    /** 共享选路实现（调试与未来 Runtime 同一语义，见类注释）。 */
    private final CandidateSelector candidateSelector = new CandidateSelector();

    /**
     * 模型调试：按主 Binding 直连上游（不经过 Route）。
     */
    public DebugInvokeResultVo modelInvoke(ModelInvokeDebugRequest request) {
        ensureDebugAllowed();
        ResolvedModel resolved = resolveModelTarget(request.modelCode());
        String payload = buildChatPayload(resolved.target(), request.systemPrompt(), request.text(),
                request.chatParams());
        DebugInvokeResultVo result = invoke(resolved.target(), payload);
        writeRequestLog(null, resolved.model(), resolved.provider(), resolved.target(), payload, result);
        configAuditService.record("DEBUG", resolved.model().getModelCode(), "MODEL_INVOKE",
                "模型调试", result.requestId());
        return result;
    }

    /**
     * 模型调试流式（SSE）：上游 chunk 逐行穿透，终态发 {@code debug-meta} 事件，
     * 失败发 {@code debug-error} 事件，均以 {@code data: [DONE]} 收尾。
     */
    public StreamingOutput modelInvokeStream(ModelInvokeDebugRequest request) {
        ensureDebugAllowed();
        ResolvedModel resolved = resolveModelTarget(request.modelCode());
        String payload = buildChatPayload(resolved.target(), request.systemPrompt(), request.text(),
                request.chatParams());
        return out -> {
            DebugInvokeResultVo result = streamInvoke(resolved.target(), payload, out);
            writeRequestLog(null, resolved.model(), resolved.provider(), resolved.target(), payload, result);
            configAuditService.record("DEBUG", resolved.model().getModelCode(), "MODEL_INVOKE",
                    "模型调试", result.requestId());
            writeTerminal(out, result, payload);
        };
    }

    /**
     * 调试限域（ADR-0001 审计 A2 修复）：Console 内推理必须可关闭、有配额。
     * {@code debugInvokeEnabled=false} 时拒绝（403）；当日 DEBUG 来源日志数达到
     * {@code debugDailyQuota} 时拒绝（429，配额 &lt; 0 表示不限量）。
     */
    private void ensureDebugAllowed() {
        if (platformSettingService == null) {
            return;
        }
        Map<String, String> settings = platformSettingService.getAll();
        if ("false".equalsIgnoreCase(settings.getOrDefault("debugInvokeEnabled", "true").trim())) {
            throw NexusException.build(NexmuxStatusCode.DEBUG_DISABLED, "debug invocation is disabled");
        }
        long quota;
        try {
            quota = Long.parseLong(settings.getOrDefault("debugDailyQuota", "200").trim());
        } catch (NumberFormatException e) {
            quota = 200L;
        }
        if (quota < 0) {
            return;
        }
        long used = requestLogDao.selectCount(new LambdaQueryWrapper<RequestLogEntity>()
                .eq(RequestLogEntity::getSource, REQUEST_LOG_SOURCE)
                .ge(RequestLogEntity::getRequestedAt, LocalDate.now().atStartOfDay()));
        if (used >= quota) {
            throw NexusException.build(NexmuxStatusCode.DEBUG_QUOTA_EXCEEDED, "debug daily quota exhausted");
        }
    }

    /** 解析模型调试目标（模型 / 主绑定供应商缺失走契约异常）。 */
    private ResolvedModel resolveModelTarget(String modelCode) {
        ModelEntity model = modelDao.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getModelCode, modelCode));
        if (model == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "model not found");
        }
        ProviderEntity provider = model.getPrimaryProviderId() == null
                ? null
                : providerDao.selectById(model.getPrimaryProviderId());
        if (provider == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "provider not found");
        }
        return new ResolvedModel(model, provider, new Target(provider, model.getPrimaryProviderModelId()));
    }

    private record ResolvedModel(ModelEntity model, ProviderEntity provider, Target target) {
    }

    /**
     * 客户端调试：客户端状态 / 路由授权 / 发布快照校验后，按发布策略选候选模型穿透。
     */
    public DebugInvokeResultVo clientInvoke(ClientInvokeDebugRequest request) {
        ensureDebugAllowed();
        ClientResolution resolution = resolveClientTarget(request);
        if (resolution.failure() != null) {
            return resolution.failure();
        }
        String payload = buildChatPayload(resolution.target(), null, request.text(), null);
        DebugInvokeResultVo result = invoke(resolution.target(), payload);
        writeRequestLog(resolution, resolution.model(),
                resolution.provider(), resolution.target(), payload, result);
        configAuditService.record("DEBUG", resolution.client().getClientCode() + "@" + resolution.route().getAlias(),
                "CLIENT_INVOKE", "客户端调试", result.requestId());
        return enrichClientResult(resolution, result);
    }

    /**
     * 客户端调试流式（SSE）：前置校验失败（客户端停用 / 路由未授权等）以
     * {@code debug-error} 事件返回；通过后上游 chunk 逐行穿透，终态 {@code debug-meta}
     * 事件带路由命中上下文（routeGroup / target / quotaPreview / rateLimitResult）。
     */
    public StreamingOutput clientInvokeStream(ClientInvokeDebugRequest request) {
        ensureDebugAllowed();
        ClientResolution resolution = resolveClientTarget(request);
        if (resolution.failure() != null) {
            return out -> {
                writeErrorEvent(out, resolution.failure());
                writeSseDone(out);
            };
        }
        String payload = buildChatPayload(resolution.target(), null, request.text(), null);
        return out -> {
            DebugInvokeResultVo result = streamInvoke(resolution.target(), payload, out);
            writeRequestLog(resolution, resolution.model(),
                    resolution.provider(), resolution.target(), payload, result);
            configAuditService.record("DEBUG",
                    resolution.client().getClientCode() + "@" + resolution.route().getAlias(),
                    "CLIENT_INVOKE", "客户端调试", result.requestId());
            writeTerminal(out, enrichClientResult(resolution, result), payload);
        };
    }

    /** 客户端调试目标解析：契约错误（不存在 / 未发布）抛 NexusException，业务拒绝（停用 / 未授权 / 无候选）入 failure。 */
    private ClientResolution resolveClientTarget(ClientInvokeDebugRequest request) {
        ClientEntity client = clientDao.selectOne(new LambdaQueryWrapper<ClientEntity>()
                .eq(ClientEntity::getClientCode, request.clientCode()));
        if (client == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client not found");
        }
        if (!"ENABLED".equals(client.getStatus())) {
            return ClientResolution.rejected(failure(403, "client is disabled"));
        }
        RouteEntity route = routeDao.selectOne(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getRouteCode, request.routeCode()));
        if (route == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "route not found");
        }
        if (!NexmuxJson.readStringList(client.getAuthorizedRouteIds()).contains(route.getRouteId())) {
            return ClientResolution.rejected(failure(403, "route is not granted to this client"));
        }
        if (!"PUBLISHED".equals(route.getStatus()) || route.getPublishedRouteSpec() == null) {
            throw NexusException.build(NexmuxStatusCode.ROUTE_NOT_PUBLISHED, "route is not published");
        }
        RouteSpec spec = NexmuxJson.read(route.getPublishedRouteSpec(), RouteSpec.class);
        // 与 Runtime 同一选路实现：策略 / 权重 / 优先级 / 粘性 / 健康过滤统一生效；
        // Console 无健康视图（传空集），会话键取客户端编码以保证 HASH / 粘性可观测。
        var selection = candidateSelector.select(spec, Set.of(), client.getClientCode());
        if (selection.isEmpty()) {
            return ClientResolution.rejected(failure(422, "no enabled candidate model in published route spec"));
        }
        String candidateModelCode = selection.get().candidate().modelCode();
        ModelEntity model = modelDao.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getModelCode, candidateModelCode));
        if (model == null) {
            return ClientResolution.rejected(failure(422, "candidate model not found: " + candidateModelCode));
        }
        ProviderEntity provider = model.getPrimaryProviderId() == null
                ? null
                : providerDao.selectById(model.getPrimaryProviderId());
        if (provider == null) {
            return ClientResolution.rejected(failure(422, "candidate provider not found"));
        }
        return new ClientResolution(client, route, spec, selection.get(), model, provider,
                new Target(provider, model.getPrimaryProviderModelId()), null);
    }

    /** 客户端调试结果增强：路由组 / 命中目标 / 配额与限流文案。 */
    private static DebugInvokeResultVo enrichClientResult(ClientResolution resolution, DebugInvokeResultVo result) {
        List<String> tags = resolution.spec().tags() == null ? List.of() : resolution.spec().tags();
        ClientEntity client = resolution.client();
        return new DebugInvokeResultVo(
                result.requestId(), result.status(), result.body(), result.latencyMs(),
                result.reply(), result.tokensIn(), result.tokensOut(), result.ttftMs(),
                tags.isEmpty() ? "default" : tags.get(0),
                resolution.model().getDisplayName() + " · " + resolution.provider().getDisplayName(),
                client.getQuotaSpec() == null || client.getQuotaSpec().isBlank()
                        ? "未配置配额（不限量）" : "按客户端配额预扣 1 次",
                client.getRateLimitSpec() == null || client.getRateLimitSpec().isBlank()
                        ? "通过（未配置限流）" : "通过（未触发限流）");
    }

    private record ClientResolution(ClientEntity client, RouteEntity route, RouteSpec spec,
                                    CandidateSelector.Selection selection,
                                    ModelEntity model, ProviderEntity provider, Target target,
                                    DebugInvokeResultVo failure) {
        static ClientResolution rejected(DebugInvokeResultVo failure) {
            return new ClientResolution(null, null, null, null, null, null, null, failure);
        }
    }

    private record Target(ProviderEntity provider, String upstreamModelId) {
    }

    private DebugInvokeResultVo invoke(Target target, String payload) {
        ProviderCredentialEntity credential = credentialOf(target.provider());
        if (credential == null) {
            return failure(422, "provider credential not configured");
        }
        String requestId = newDebugRequestId();
        long start = System.currentTimeMillis();
        try {
            String body = postChatCompletions(target, credentialCipher.decrypt(credential.getCiphertext()).trim(), payload);
            int latency = (int) (System.currentTimeMillis() - start);
            JsonNode parsed = readJsonTolerant(body);
            JsonNode usage = parsed == null ? null : parsed.get("usage");
            return new DebugInvokeResultVo(
                    requestId, 200, body, latency,
                    replyOf(parsed),
                    longOrNull(usage, "prompt_tokens"),
                    longOrNull(usage, "completion_tokens"),
                    latency, null, null, null, null);
        } catch (IOException e) {
            return new DebugInvokeResultVo(
                    requestId, 502,
                    "{\"error\":\"upstream call failed: " + e.getMessage().replace("\"", "'") + "\"}",
                    (int) (System.currentTimeMillis() - start),
                    null, null, null, null, null, null, null, null);
        }
    }

    /** SSE 事件名：终态元数据 / 调用失败（data 均为单行 JSON 对象）。 */
    private static final String EVENT_META = "debug-meta";
    private static final String EVENT_ERROR = "debug-error";
    private static final String SSE_DONE_LINE = "data: [DONE]";

    /**
     * 流式穿透：强制 {@code stream + include_usage} 调上游，SSE 逐行转发给 out
     * （抑制上游 [DONE]，由终态统一收尾），同时聚合 reply 与 usage 供请求日志留存。
     * 凭证缺失 / 上游失败与非流式一致以失败结果体返回（由调用方转 debug-error 事件）。
     */
    private DebugInvokeResultVo streamInvoke(Target target, String payload, OutputStream out) {
        ProviderCredentialEntity credential = credentialOf(target.provider());
        if (credential == null) {
            return failure(422, "provider credential not configured");
        }
        String requestId = newDebugRequestId();
        long start = System.currentTimeMillis();
        int ttftMs = -1;
        StringBuilder reply = new StringBuilder();
        Long tokensIn = null;
        Long tokensOut = null;
        Map<String, Object> body = NexmuxJson.read(payload, new TypeReference<>() {});
        body.put("model", target.upstreamModelId());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        HttpURLConnection connection = null;
        try {
            connection = connect(target, credentialCipher.decrypt(credential.getCiphertext()).trim(), body);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (SSE_DONE_LINE.equals(line)) {
                    continue;
                }
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (ttftMs < 0 && !line.isBlank()) {
                    ttftMs = (int) (System.currentTimeMillis() - start);
                }
                if (!line.startsWith("data: ")) {
                    continue;
                }
                JsonNode chunk = readJsonTolerant(line.substring(6));
                if (chunk == null) {
                    continue;
                }
                JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                if (delta.isTextual()) {
                    reply.append(delta.asText());
                }
                JsonNode usage = chunk.get("usage");
                if (usage != null && usage.isObject()) {
                    tokensIn = longOrNull(usage, "prompt_tokens");
                    tokensOut = longOrNull(usage, "completion_tokens");
                }
            }
            int latency = (int) (System.currentTimeMillis() - start);
            return new DebugInvokeResultVo(
                    requestId, 200, synthesizedResponse(requestId, target, reply, tokensIn, tokensOut), latency,
                    reply.toString(), tokensIn, tokensOut,
                    ttftMs < 0 ? latency : ttftMs, null, null, null, null);
        } catch (IOException e) {
            return new DebugInvokeResultVo(
                    requestId, 502,
                    "{\"error\":\"upstream call failed: " + e.getMessage().replace("\"", "'") + "\"}",
                    (int) (System.currentTimeMillis() - start),
                    null, null, null, null, null, null, null, null);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 终态：成功发 debug-meta（含 requestJson / responseJson 供返回报文面板），失败发 debug-error，再补 [DONE]。 */
    private static void writeTerminal(OutputStream out, DebugInvokeResultVo result, String payload)
            throws IOException {
        if (result.status() == 200) {
            writeSseEvent(out, EVENT_META, metaJson(result, payload));
        } else {
            writeErrorEvent(out, result);
        }
        writeSseDone(out);
    }

    /** debug-meta 事件负载：空值字段不输出。 */
    private static String metaJson(DebugInvokeResultVo result, String requestJson) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", result.requestId());
        meta.put("status", result.status());
        meta.put("latencyMs", result.latencyMs());
        meta.put("ttftMs", result.ttftMs());
        if (result.tokensIn() != null) {
            meta.put("tokensIn", result.tokensIn());
        }
        if (result.tokensOut() != null) {
            meta.put("tokensOut", result.tokensOut());
        }
        if (result.routeGroup() != null) {
            meta.put("routeGroup", result.routeGroup());
        }
        if (result.target() != null) {
            meta.put("target", result.target());
        }
        if (result.quotaPreview() != null) {
            meta.put("quotaPreview", result.quotaPreview());
        }
        if (result.rateLimitResult() != null) {
            meta.put("rateLimitResult", result.rateLimitResult());
        }
        meta.put("requestJson", requestJson);
        meta.put("responseJson", result.body());
        return NexmuxJson.write(meta);
    }

    /** debug-error 事件负载：message 取自结果体 error 字段（非 JSON 时截断原文）。 */
    private static void writeErrorEvent(OutputStream out, DebugInvokeResultVo result) throws IOException {
        JsonNode body = readJsonTolerant(result.body());
        JsonNode error = body == null ? null : body.get("error");
        String message = error != null && error.isTextual() ? error.asText() : abbreviate(result.body());
        writeSseEvent(out, EVENT_ERROR, NexmuxJson.write(Map.of(
                "requestId", result.requestId(),
                "status", result.status(),
                "message", message)));
    }

    private static void writeSseEvent(OutputStream out, String event, String data) throws IOException {
        out.write(("event: " + event + "\n" + "data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void writeSseDone(OutputStream out) throws IOException {
        out.write((SSE_DONE_LINE + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 流式响应聚合为 chat.completion 形态（请求日志 usage 解析与返回报文面板复用）。 */
    private static String synthesizedResponse(String requestId, Target target, StringBuilder reply,
                                              Long tokensIn, Long tokensOut) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", requestId);
        body.put("object", "chat.completion");
        body.put("model", target.upstreamModelId());
        body.put("choices", List.of(Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", reply.toString()),
                "finish_reason", "stop")));
        if (tokensIn != null || tokensOut != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", tokensIn == null ? 0 : tokensIn);
            usage.put("completion_tokens", tokensOut == null ? 0 : tokensOut);
            body.put("usage", usage);
        }
        return NexmuxJson.write(body);
    }

    private ProviderCredentialEntity credentialOf(ProviderEntity provider) {
        ProviderCredentialEntity credential = providerCredentialDao.selectOne(
                new LambdaQueryWrapper<ProviderCredentialEntity>()
                        .eq(ProviderCredentialEntity::getProviderId, provider.getProviderId()));
        return credential == null || credential.getCiphertext() == null || credential.getCiphertext().isBlank()
                ? null : credential;
    }

    private static String newDebugRequestId() {
        return "dbg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 组装 OpenAI 兼容请求体（model 由发送层替换为上游 ID；chatParams 平铺合并）。 */
    private static String buildChatPayload(Target target, String systemPrompt, String text,
                                           Map<String, Object> chatParams) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", target.upstreamModelId());
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", text == null ? "" : text));
        body.put("messages", messages);
        if (chatParams != null) {
            chatParams.forEach((key, value) -> {
                if (value != null) {
                    body.put(key, value);
                }
            });
        }
        return NexmuxJson.write(body);
    }

    private static String replyOf(JsonNode body) {
        JsonNode choices = body == null ? null : body.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        JsonNode content = message == null ? null : message.get("content");
        return content == null || !content.isTextual() ? null : content.asText();
    }

    /** OpenAI 兼容 POST {baseUrl}/chat/completions（非流式；payload 的 model 替换为上游 ID）。 */
    private String postChatCompletions(Target target, String apiKey, String payload) throws IOException {
        Map<String, Object> body = NexmuxJson.read(payload, new TypeReference<>() {});
        body.put("model", target.upstreamModelId());
        body.put("stream", false);
        HttpURLConnection connection = connect(target, apiKey, body);
        try {
            InputStream in = connection.getInputStream();
            return in == null
                    ? "{\"error\":\"empty upstream response\"}"
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /** 建立上游连接并写入请求体；HTTP >= 400 时读取错误流抛 IOException（连接已断开）。 */
    private HttpURLConnection connect(Target target, String apiKey, Map<String, Object> body) throws IOException {
        URI uri = URI.create(target.provider().getBaseUrl().replaceAll("/+$", "") + "/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        int configured = upstreamTimeoutMs();
        int timeout = Math.min(
                target.provider().getTimeoutMs() == null ? configured : target.provider().getTimeoutMs(),
                configured);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        byte[] bytes = NexmuxJson.write(body).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        int status = connection.getResponseCode();
        if (status >= 400) {
            InputStream err = connection.getErrorStream();
            String response = err == null
                    ? "{\"error\":\"empty upstream response\"}"
                    : new String(err.readAllBytes(), StandardCharsets.UTF_8);
            connection.disconnect();
            throw new IOException("HTTP " + status + ": " + abbreviate(response));
        }
        return connection;
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    private static DebugInvokeResultVo failure(int httpStatus, String message) {
        return new DebugInvokeResultVo(
                "dbg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                httpStatus, "{\"error\":\"" + message + "\"}", 0,
                null, null, null, null, null, null, null, null);
    }

    /**
     * 调试调用落请求日志（与 Runtime 同表 mux_request_log，显式标记 {@code source=DEBUG}
     * 与真实流量区分，requestId 即 DebugInvokeResultVo.requestId）：用量页 / 成本页 / 调用日志直接可读。
     * 派生字段与落库由共享 {@link RequestLogWriter} 完成（与 Runtime 同一计量语义）。
     */
    private void writeRequestLog(ClientResolution resolution, ModelEntity model, ProviderEntity provider,
                                 Target target, String payload, DebugInvokeResultVo result) {
        ClientEntity client = resolution == null ? null : resolution.client();
        RouteEntity route = resolution == null ? null : resolution.route();
        RouteSpec spec = resolution == null ? null : resolution.spec();
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(result.requestId());
        entity.setSource(REQUEST_LOG_SOURCE);
        entity.setClientId(client == null ? null : client.getClientId());
        entity.setRouteId(route == null ? null : route.getRouteId());
        entity.setModelCode(model.getModelCode());
        entity.setStatus(result.status() == 200 ? "SUCCESS" : "ERROR");
        entity.setLatencyMs((long) result.latencyMs());
        entity.setErrorCode(result.status() == 200 ? null : "HTTP_" + result.status());
        entity.setRequestedAt(LocalDateTime.now());
        entity.setAppKeyId(client == null ? null : ClientService.appKeyIdOf(client));
        entity.setProviderCode(provider.getProviderCode());
        entity.setProtocol(provider.getDefaultProtocol());
        entity.setRouteGroup(client == null ? "model-debug" : "client-debug");
        entity.setApplication(client == null ? "控制台·模型调试" : client.getDisplayName());
        entity.setAlias(route == null ? null : route.getAlias());
        entity.setHttpStatus(result.status());
        entity.setUpstreamModel(target.upstreamModelId());
        entity.setUpstreamEndpoint(provider.getBaseUrl());
        entity.setUpstreamStatus(String.valueOf(result.status()));
        entity.setUpstreamLatencyMs((long) result.latencyMs());
        entity.setRetries(0);
        entity.setIngressMethod("POST");
        entity.setIngressPath(route == null ? "/debug/model-invoke" : route.getRouteSpec() == null
                ? null : NexmuxJson.read(route.getPublishedRouteSpec(), RouteSpec.class).ingress());
        // routeTrace：选路上下文随日志留存（策略 / 命中候选 / 候选池 / 健康过滤 / 粘性），
        // 与 Runtime 同一 CandidateSelector 语义，供「调试通过 == 上线一致」核对。
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("debug", true);
        trace.put("strategy", spec == null || spec.strategyType() == null
                ? (spec == null ? "DIRECT" : "FIXED") : spec.strategyType());
        trace.put("candidate", target.upstreamModelId());
        if (resolution != null && resolution.selection() != null) {
            trace.put("candidateModelCode", resolution.selection().candidate().modelCode());
            trace.put("poolSize", resolution.selection().poolSize());
            trace.put("healthFilterApplied", resolution.selection().healthFilterApplied());
            trace.put("stickyApplied", resolution.selection().stickyApplied());
        }
        entity.setRouteTrace(NexmuxJson.write(trace));

        // 派生字段（usage / 成本与定价快照 / prompt 快照）与落库统一走共享写入器
        requestLogWriter.write(entity, model.getPricingSpec(), null, null, payload, result.body());
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

    /** 上游连接超时：服务设置 upstreamConnectTimeoutMs（缺失 / 非法回退常量）。 */
    private int upstreamTimeoutMs() {
        if (platformSettingService == null) {
            return UPSTREAM_TIMEOUT_MS;
        }
        try {
            return Integer.parseInt(
                    platformSettingService.getAll().getOrDefault("upstreamConnectTimeoutMs",
                            String.valueOf(UPSTREAM_TIMEOUT_MS)).trim());
        } catch (NumberFormatException e) {
            return UPSTREAM_TIMEOUT_MS;
        }
    }
}
