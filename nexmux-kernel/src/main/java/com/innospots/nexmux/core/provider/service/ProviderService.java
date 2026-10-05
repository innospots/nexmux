package com.innospots.nexmux.core.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderDetailVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderModelRowVo;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.provider.domain.vo.UpstreamModelVo;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProviderService {

    /** 密钥状态取值（领域值，非 StatusCode）。 */
    public static final String KEY_STATUS_CONFIGURED = "configured";
    public static final String KEY_STATUS_PENDING = "pending";
    public static final String KEY_STATUS_NONE = "none";

    private static final int CONNECT_PROBE_TIMEOUT_MS = 5000;
    private static final int UPSTREAM_FETCH_TIMEOUT_MS = 10000;

    private final ProviderDao providerDao;
    private final ProviderCredentialDao providerCredentialDao;
    private final ModelDao modelDao;

    public PageResult<ProviderVo> page(ProviderPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = buildPageWrapper(request);
        long total = providerDao.selectCount(wrapper);
        var records = providerDao.selectList(wrapper
                        .orderByDesc(ProviderEntity::getCreatedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public ProviderVo get(String providerId) {
        return toVo(require(providerId));
    }

    /**
     * 按稳定业务键查询详情（含密钥状态与模型行；不含明文密钥）。
     */
    public ProviderDetailVo getByCode(String providerCode) {
        ProviderEntity entity = providerDao.selectOne(new LambdaQueryWrapper<ProviderEntity>()
                .eq(ProviderEntity::getProviderCode, providerCode));
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "provider not found: " + providerCode);
        }
        ProviderCredentialEntity credential = credentialOf(entity.getProviderId());
        List<ProviderModelRowVo> models = modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                        .eq(ModelEntity::getPrimaryProviderId, entity.getProviderId())
                        .orderByAsc(ModelEntity::getCreatedAt))
                .stream()
                .map(m -> new ProviderModelRowVo(
                        m.getModelCode(), m.getDisplayName(), m.getModelType(),
                        m.getPrimaryProviderModelId(), m.getStatus()))
                .toList();
        return new ProviderDetailVo(
                entity.getProviderId(),
                entity.getProviderCode(),
                entity.getDisplayName(),
                entity.getProviderType(),
                entity.getDefaultProtocol(),
                entity.getVendorCode(),
                entity.getSourceVendorCode(),
                entity.getIconUri(),
                entity.getBaseUrl(),
                entity.getRegion(),
                entity.getTimeoutMs(),
                entity.getProxy(),
                entity.getDescription(),
                entity.getCapabilityExtension(),
                entity.getStatus(),
                keyStatusOf(credential),
                credential == null ? null : credential.getKeyVersion(),
                credential == null ? null : credential.getRotatedAt(),
                models,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public ProviderVo create(ProviderCreateRequest request) {
        if (providerDao.selectCount(new LambdaQueryWrapper<ProviderEntity>()
                .eq(ProviderEntity::getProviderCode, request.providerCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "provider code exists");
        }
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderCode(request.providerCode());
        entity.setDisplayName(request.displayName());
        entity.setProviderType(request.providerType());
        entity.setDefaultProtocol(request.defaultProtocol());
        entity.setVendorCode(request.vendorCode());
        entity.setSourceVendorCode(
                request.sourceVendorCode() == null ? request.vendorCode() : request.sourceVendorCode());
        entity.setIconUri(request.iconUri());
        entity.setBaseUrl(request.baseUrl());
        entity.setRegion(request.region());
        entity.setTimeoutMs(request.timeoutMs() == null ? 60000 : request.timeoutMs());
        entity.setStatus("ENABLED");
        providerDao.insert(entity);
        return toVo(entity);
    }

    public ProviderVo update(String providerId, ProviderUpdateRequest request) {
        ProviderEntity entity = require(providerId);
        entity.setDisplayName(request.displayName());
        entity.setBaseUrl(request.baseUrl());
        entity.setRegion(request.region());
        if (request.timeoutMs() != null) {
            entity.setTimeoutMs(request.timeoutMs());
        }
        providerDao.updateById(entity);
        return toVo(entity);
    }

    public ProviderVo setEnabled(String providerId, boolean enabled) {
        ProviderEntity entity = require(providerId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        providerDao.updateById(entity);
        return toVo(entity);
    }

    public ProviderVo disable(String providerId) {
        return setEnabled(providerId, false);
    }

    /**
     * 连通性测试：未配置凭证时快速失败；否则按 baseUrl 做 TCP 连接探测。
     */
    public ConnectionTestVo connectionTest(String providerId) {
        ProviderEntity entity = require(providerId);
        ProviderCredentialEntity credential = credentialOf(providerId);
        if (!hasSecret(credential)) {
            return new ConnectionTestVo(false, 0, "未配置 API Key");
        }
        long start = System.currentTimeMillis();
        try {
            URI uri = URI.create(entity.getBaseUrl());
            int port = uri.getPort() > 0
                    ? uri.getPort()
                    : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_PROBE_TIMEOUT_MS);
            }
            return new ConnectionTestVo(
                    true, System.currentTimeMillis() - start, entity.getBaseUrl() + " 可达");
        } catch (Exception e) {
            return new ConnectionTestVo(
                    false, System.currentTimeMillis() - start, "连接失败: " + e.getMessage());
        }
    }

    /**
     * 拉取上游模型清单（OpenAI 兼容 {@code GET {baseUrl}/models}），并标记本地是否已建模。
     */
    public List<UpstreamModelVo> pullModels(String providerId) {
        ProviderEntity entity = require(providerId);
        ProviderCredentialEntity credential = credentialOf(providerId);
        if (!hasSecret(credential)) {
            throw NexusException.build(
                    NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "provider credential required before pulling models");
        }
        return toUpstreamRows(fetchUpstreamModelIds(entity, credential), providerId);
    }

    /**
     * 上游模型 ID → 行（标记本地主 Binding 是否已引用；包可见供单测）。
     */
    List<UpstreamModelVo> toUpstreamRows(List<String> upstreamIds, String providerId) {
        Set<String> localIds = modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                        .eq(ModelEntity::getPrimaryProviderId, providerId))
                .stream()
                .map(ModelEntity::getPrimaryProviderModelId)
                .collect(Collectors.toSet());
        return upstreamIds.stream()
                .distinct()
                .map(id -> new UpstreamModelVo(id, localIds.contains(id)))
                .toList();
    }

    private List<String> fetchUpstreamModelIds(ProviderEntity entity, ProviderCredentialEntity credential) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(entity.getBaseUrl().replaceAll("/+$", "") + "/models");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + credential.getCiphertext().trim());
            connection.setConnectTimeout(UPSTREAM_FETCH_TIMEOUT_MS);
            connection.setReadTimeout(UPSTREAM_FETCH_TIMEOUT_MS);
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw NexusException.build(
                        NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                        "upstream models fetch failed: HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> payload = NexmuxJson.read(body, new TypeReference<>() {});
                Object data = payload == null ? null : payload.get("data");
                if (!(data instanceof List<?> list)) {
                    return List.of();
                }
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> ((Map<?, ?>) item).get("id"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }
        } catch (IOException e) {
            throw NexusException.build(
                    NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "upstream models fetch failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private LambdaQueryWrapper<ProviderEntity> buildPageWrapper(ProviderPageRequest request) {
        // count 与 list 共用条件 wrapper；排序在 list 侧追加（H2 PG 模式下 count 带 order 报 GROUP BY 错）
        var wrapper = new LambdaQueryWrapper<ProviderEntity>();
        if (request.keyword() != null && !request.keyword().isBlank()) {
            String keyword = request.keyword().trim();
            wrapper.and(w -> w.like(ProviderEntity::getDisplayName, keyword)
                    .or().like(ProviderEntity::getProviderCode, keyword)
                    .or().like(ProviderEntity::getBaseUrl, keyword));
        }
        if (request.status() != null && !request.status().isBlank()) {
            wrapper.eq(ProviderEntity::getStatus, request.status());
        }
        if (request.types() != null && !request.types().isEmpty()) {
            wrapper.in(ProviderEntity::getVendorCode, request.types());
        }
        applyKeyStatusFilter(wrapper, request.keyStatus());
        return wrapper;
    }

    /**
     * 密钥状态过滤（单表查询后内存收敛为 providerId 集合，避免 join）。
     */
    private void applyKeyStatusFilter(LambdaQueryWrapper<ProviderEntity> wrapper, String keyStatus) {
        if (keyStatus == null || keyStatus.isBlank()) {
            return;
        }
        List<ProviderCredentialEntity> credentials = providerCredentialDao.selectList(null);
        if (KEY_STATUS_NONE.equals(keyStatus)) {
            List<String> withCredential = credentials.stream()
                    .map(ProviderCredentialEntity::getProviderId)
                    .toList();
            if (!withCredential.isEmpty()) {
                wrapper.notIn(ProviderEntity::getProviderId, withCredential);
            }
            return;
        }
        if (KEY_STATUS_PENDING.equals(keyStatus)) {
            List<String> pending = credentials.stream()
                    .filter(c -> !hasSecret(c))
                    .map(ProviderCredentialEntity::getProviderId)
                    .toList();
            if (pending.isEmpty()) {
                // 无命中：以不可能条件短路
                wrapper.eq(ProviderEntity::getProviderId, "__none__");
            } else {
                wrapper.in(ProviderEntity::getProviderId, pending);
            }
        }
    }

    private ProviderEntity require(String providerId) {
        ProviderEntity entity = providerDao.selectById(providerId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "provider not found");
        }
        return entity;
    }

    private ProviderCredentialEntity credentialOf(String providerId) {
        return providerCredentialDao.selectOne(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getProviderId, providerId));
    }

    static String keyStatusOf(ProviderCredentialEntity credential) {
        if (credential == null) {
            return KEY_STATUS_NONE;
        }
        return hasSecret(credential) ? KEY_STATUS_CONFIGURED : KEY_STATUS_PENDING;
    }

    private static boolean hasSecret(ProviderCredentialEntity credential) {
        if (credential == null) {
            return false;
        }
        return credential.getCiphertext() != null && !credential.getCiphertext().isBlank()
                || credential.getSecretReference() != null && !credential.getSecretReference().isBlank();
    }

    private ProviderVo toVo(ProviderEntity entity) {
        ProviderCredentialEntity credential = credentialOf(entity.getProviderId());
        long modelCount = modelDao.selectCount(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getPrimaryProviderId, entity.getProviderId()));
        return new ProviderVo(
                entity.getProviderId(),
                entity.getProviderCode(),
                entity.getDisplayName(),
                entity.getProviderType(),
                entity.getDefaultProtocol(),
                entity.getVendorCode(),
                entity.getSourceVendorCode(),
                entity.getIconUri(),
                entity.getBaseUrl(),
                entity.getRegion(),
                entity.getTimeoutMs(),
                entity.getStatus(),
                keyStatusOf(credential),
                modelCount);
    }
}
