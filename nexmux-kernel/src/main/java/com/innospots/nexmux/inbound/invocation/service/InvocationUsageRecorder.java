package com.innospots.nexmux.inbound.invocation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.service.RequestLogWriter;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.inbound.invocation.domain.model.InvocationContext;
import com.innospots.nexmux.inbound.invocation.domain.model.InvocationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 调用计量：配额/限流记账与 {@code mux_request_log} 落库。
 *
 * <p>成功（HTTP 200）时按 tokens 与成本回填配额与 TPM 计数；成功与失败都写调用日志
 * （{@code source=RUNTIME}），保证对账口径完整。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see RequestLogWriter
 * @see QuotaGate
 * @see RateLimitGate
 */
@RequiredArgsConstructor
public final class InvocationUsageRecorder {

    /** Runtime 写入 {@code mux_request_log.source} 的标记。 */
    public static final String REQUEST_LOG_SOURCE = "RUNTIME";

    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_GATEWAY = 502;

    private final QuotaGate quotaGate;
    private final RateLimitGate rateLimitGate;
    private final RequestLogWriter requestLogWriter;
    private final RequestLogDao requestLogDao;

    /**
     * 收尾：成功时记账，随后落库调用日志。
     *
     * @param context     解析上下文
     * @param result      调用结果
     * @param requestBody 客户端原始请求体（日志留存）
     */
    public void settle(InvocationContext context, InvocationResult result,
                       Map<String, Object> requestBody) {
        long tokensIn = result.tokensIn() == null ? 0 : result.tokensIn();
        long tokensOut = result.tokensOut() == null ? 0 : result.tokensOut();
        if (result.httpStatus() == HTTP_OK) {
            quotaGate.recordUsage(context.client(), tokensIn, tokensOut, null);
            rateLimitGate.recordTokens(
                    context.client(), context.route().routeId(), tokensIn + tokensOut);
        }
        RequestLogEntity entity = writeRequestLog(context, result, requestBody);
        if (result.httpStatus() == HTTP_OK && entity.getCostUsd() != null) {
            quotaGate.recordUsage(context.client(), 0, 0, entity.getCostUsd());
        }
    }

    /**
     * 以运行库聚合回填配额计数（进程重启 / 周期校准）。
     *
     * <p>对配置了配额规则的客户端，按当前生效窗口聚合 {@code mux_request_log}
     * （仅 {@code source=RUNTIME}），差额并入内存计数器。</p>
     *
     * @param clients 全量客户端快照
     */
    public void resyncQuotas(Iterable<ClientEntity> clients) {
        for (ClientEntity client : clients) {
            java.util.List<QuotaGate.QuotaRuleSpec> rules = QuotaGate.enabledRules(client);
            if (rules.isEmpty()) {
                continue;
            }
            LocalDateTime earliest = rules.stream()
                    .map(quotaGate::windowStart)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
            // 聚合别名须加双引号保留大小写（同 StatsQueryDao 约定）：
            // H2 DATABASE_TO_LOWER=TRUE 会把未加引号的别名小写化（label=costusd），导致驼峰键取不到值
            java.util.List<Map<String, Object>> rows = requestLogDao.selectMaps(
                    new QueryWrapper<RequestLogEntity>()
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
            long tokensIn = numberOf(valueOf(row, "inputTokens"));
            long tokensOut = numberOf(valueOf(row, "outputTokens"));
            Map<String, Long> usage = Map.of(
                    "REQUEST", numberOf(valueOf(row, "requestCount")),
                    "INPUT_TOKEN", tokensIn,
                    "OUTPUT_TOKEN", tokensOut,
                    "TOTAL_TOKEN", tokensIn + tokensOut,
                    // COST 计数刻度与 QuotaGate 一致：1e-8 美元
                    "COST", scaledCostOf(valueOf(row, "costUsd")));
            quotaGate.resync(client.getClientId(), rules, usage);
        }
    }

    private RequestLogEntity writeRequestLog(InvocationContext context, InvocationResult result,
                                             Map<String, Object> requestBody) {
        ClientEntity client = context.client();
        ProviderEntity provider = context.provider();
        RouteSpec spec = context.route().spec();
        boolean success = result.httpStatus() == HTTP_OK;
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(context.requestId());
        entity.setTraceId(context.traceId());
        entity.setSource(REQUEST_LOG_SOURCE);
        entity.setClientId(client.getClientId());
        entity.setRouteId(context.route().routeId());
        entity.setModelCode(context.model().getModelCode());
        entity.setStatus(success ? "SUCCESS" : "ERROR");
        entity.setLatencyMs(result.latencyMs());
        entity.setTtftMs(result.ttftMs() < 0 ? null : result.ttftMs());
        entity.setErrorCode(success ? null : "HTTP_" + result.httpStatus());
        entity.setErrorType(success ? null : (result.httpStatus() >= HTTP_BAD_GATEWAY
                ? "UPSTREAM" : "REQUEST"));
        entity.setRequestedAt(LocalDateTime.now());
        entity.setAppKeyId(com.innospots.nexmux.core.access.service.ClientService.appKeyIdOf(client));
        entity.setProviderCode(provider.getProviderCode());
        entity.setProtocol(context.binding().upstreamProtocol() != null
                ? context.binding().upstreamProtocol() : provider.getDefaultProtocol());
        entity.setRouteGroup(spec.tags() == null || spec.tags().isEmpty()
                ? "default" : spec.tags().get(0));
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
                NexmuxJson.write(requestBody), result.body(),
                provider.getProviderId(), context.binding().providerModelId(),
                context.binding().primary());
        return entity;
    }

    /** routeTrace：选路 / Binding / 配额 / 限流上下文全量留存。 */
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

    /** 取聚合行值：优先原键，回退小写键（防御未加引号别名被小写化的方言差异）。 */
    private static Object valueOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toLowerCase(Locale.ROOT));
    }

    /** COST 聚合计值换算（1e-8 美元刻度），null 安全。 */
    private static long scaledCostOf(Object value) {
        return value == null
                ? 0 : new BigDecimal(String.valueOf(value)).movePointRight(8).longValue();
    }

    private static long numberOf(Object value) {
        return value == null ? 0 : new BigDecimal(String.valueOf(value)).longValue();
    }
}
