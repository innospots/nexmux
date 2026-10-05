package com.innospots.nexmux.core.debug.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
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
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 调试台真实穿透调用（设计 §4.3：模型调试直连 Binding，客户端调试模拟全链路）。
 * 失败以结果体返回（httpStatus + 可读 body），不抛业务异常；
 * 目标不存在 / 路由未发布等契约错误仍走 NexusException。
 */
@RequiredArgsConstructor
public class DebugService {

    private static final int UPSTREAM_TIMEOUT_MS = 30000;

    private final ModelDao modelDao;
    private final ProviderDao providerDao;
    private final ProviderCredentialDao providerCredentialDao;
    private final ClientDao clientDao;
    private final RouteDao routeDao;
    private final ConfigAuditService configAuditService;

    /**
     * 模型调试：按主 Binding 直连上游（不经过 Route）。
     */
    public DebugInvokeResultVo modelInvoke(ModelInvokeDebugRequest request) {
        ModelEntity model = modelDao.selectById(request.modelId());
        if (model == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "model not found");
        }
        ProviderEntity provider = model.getPrimaryProviderId() == null
                ? null
                : providerDao.selectById(model.getPrimaryProviderId());
        if (provider == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "provider not found");
        }
        Target target = new Target(provider, model.getPrimaryProviderModelId());
        DebugInvokeResultVo result = invoke(target, request.payload());
        configAuditService.record("DEBUG", model.getModelCode(), "MODEL_INVOKE",
                "模型调试", result.requestId());
        return result;
    }

    /**
     * 客户端调试：客户端状态 / 路由授权 / 发布快照校验后，按发布策略选候选模型穿透。
     */
    public DebugInvokeResultVo clientInvoke(ClientInvokeDebugRequest request) {
        ClientEntity client = clientDao.selectById(request.clientId());
        if (client == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client not found");
        }
        if (!"ENABLED".equals(client.getStatus())) {
            return failure(403, "client is disabled");
        }
        RouteEntity route = routeDao.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<RouteEntity>().eq(RouteEntity::getAlias, request.routeAlias()));
        if (route == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "route not found");
        }
        if (!NexmuxJson.readStringList(client.getAuthorizedRouteIds()).contains(route.getRouteId())) {
            return failure(403, "route is not granted to this client");
        }
        if (!"PUBLISHED".equals(route.getStatus()) || route.getPublishedRouteSpec() == null) {
            throw NexusException.build(NexmuxStatusCode.ROUTE_NOT_PUBLISHED, "route is not published");
        }
        RouteSpec spec = NexmuxJson.read(route.getPublishedRouteSpec(), RouteSpec.class);
        var candidate = spec.candidates().stream()
                .filter(c -> c.enabled() == null || c.enabled())
                .findFirst();
        if (candidate.isEmpty()) {
            return failure(422, "no enabled candidate model in published route spec");
        }
        ModelEntity model = modelDao.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<ModelEntity>().eq(ModelEntity::getModelCode, candidate.get().modelCode()));
        if (model == null) {
            return failure(422, "candidate model not found: " + candidate.get().modelCode());
        }
        ProviderEntity provider = model.getPrimaryProviderId() == null
                ? null
                : providerDao.selectById(model.getPrimaryProviderId());
        if (provider == null) {
            return failure(422, "candidate provider not found");
        }
        Target target = new Target(provider, model.getPrimaryProviderModelId());
        DebugInvokeResultVo result = invoke(target, request.payload());
        configAuditService.record("DEBUG", client.getClientCode() + "@" + route.getAlias(),
                "CLIENT_INVOKE", "客户端调试", result.requestId());
        return result;
    }

    private record Target(ProviderEntity provider, String upstreamModelId) {
    }

    private DebugInvokeResultVo invoke(Target target, String payload) {
        ProviderCredentialEntity credential = providerCredentialDao.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query
                        .LambdaQueryWrapper<ProviderCredentialEntity>()
                        .eq(ProviderCredentialEntity::getProviderId, target.provider().getProviderId()));
        if (credential == null || credential.getCiphertext() == null || credential.getCiphertext().isBlank()) {
            return failure(422, "provider credential not configured");
        }
        String requestId = "dbg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long start = System.currentTimeMillis();
        try {
            String body = postChatCompletions(target, credential.getCiphertext().trim(), payload);
            return new DebugInvokeResultVo(
                    requestId, "TEST", 200, body, (int) (System.currentTimeMillis() - start));
        } catch (IOException e) {
            return new DebugInvokeResultVo(
                    requestId, "TEST", 502,
                    "{\"error\":\"upstream call failed: " + e.getMessage().replace("\"", "'") + "\"}",
                    (int) (System.currentTimeMillis() - start));
        }
    }

    /** OpenAI 兼容 POST {baseUrl}/chat/completions（payload 的 model 替换为上游 ID）。 */
    private String postChatCompletions(Target target, String apiKey, String payload) throws IOException {
        Map<String, Object> body = NexmuxJson.read(payload, new TypeReference<>() {});
        body.put("model", target.upstreamModelId());
        body.put("stream", false);
        URI uri = URI.create(target.provider().getBaseUrl().replaceAll("/+$", "") + "/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            int timeout = Math.min(
                    target.provider().getTimeoutMs() == null ? UPSTREAM_TIMEOUT_MS : target.provider().getTimeoutMs(),
                    UPSTREAM_TIMEOUT_MS);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            byte[] bytes = NexmuxJson.write(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = in == null
                    ? "{\"error\":\"empty upstream response\"}"
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (status >= 400) {
                throw new IOException("HTTP " + status + ": " + abbreviate(response));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    private static DebugInvokeResultVo failure(int httpStatus, String message) {
        return new DebugInvokeResultVo(
                "dbg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "TEST", httpStatus, "{\"error\":\"" + message + "\"}", 0);
    }
}
