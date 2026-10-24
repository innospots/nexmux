package com.innospots.nexmux.inbound.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.inbound.pipeline.ModelBindingSelector;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binding 主动探活（nexmux-runtime-design §6.1/§6.4）：按周期对每个启用的
 * Provider 发起 {@code GET {baseUrl}/models}（OpenAI 兼容），结果写入
 * {@link BindingHealthRegistry}（选路实时视图）并低频 upsert {@code mux_binding_health}
 * （Console 供应商可用性页数据源）。探测流量不落 {@code mux_request_log}。
 */
public final class BindingHealthProber {

    private static final Logger log = LoggerFactory.getLogger(BindingHealthProber.class);

    private final RuntimeCatalogCache catalogCache;
    private final BindingHealthRegistry registry;
    private final BindingHealthDao healthDao;
    private final int probeTimeoutMs;
    private final com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher;

    public BindingHealthProber(RuntimeCatalogCache catalogCache, BindingHealthRegistry registry,
                               BindingHealthDao healthDao, int probeTimeoutMs,
                               com.innospots.nexmux.core.shared.security.CredentialCipher credentialCipher) {
        this.catalogCache = catalogCache;
        this.registry = registry;
        this.healthDao = healthDao;
        this.probeTimeoutMs = probeTimeoutMs;
        this.credentialCipher = credentialCipher;
    }

    /** 全量探测一轮；返回探测的 Provider 数。单 Provider 失败不影响其他。 */
    public int probeAll() {
        int probed = 0;
        Map<String, List<TargetModel>> targetsByProvider = collectTargets();
        for (Map.Entry<String, List<TargetModel>> entry : targetsByProvider.entrySet()) {
            String providerId = entry.getKey();
            Optional<ProviderEntity> provider = catalogCache.findProvider(providerId);
            if (provider.isEmpty() || !"ENABLED".equals(provider.get().getStatus())) {
                continue;
            }
            Optional<ProviderCredentialEntity> credential = catalogCache.findCredential(providerId);
            if (credential.isEmpty() || credential.get().getCiphertext() == null
                    || credential.get().getCiphertext().isBlank()) {
                continue;
            }
            probed++;
            ProbeOutcome outcome = probe(provider.get(),
                    credentialCipher.decrypt(credential.get().getCiphertext()).trim());
            for (TargetModel target : entry.getValue()) {
                applyOutcome(target, outcome);
            }
        }
        return probed;
    }

    /** 探测目标收集：启用模型的全部启用 Binding 按 provider 分组。 */
    private Map<String, List<TargetModel>> collectTargets() {
        Map<String, List<TargetModel>> targets = new LinkedHashMap<>();
        for (ModelEntity model : catalogCache.models()) {
            if (!"ENABLED".equals(model.getStatus())) {
                continue;
            }
            for (ModelBindingSelector.ResolvedBinding binding : ModelBindingSelector.bindingsOf(model)) {
                targets.computeIfAbsent(binding.providerId(), k -> new ArrayList<>())
                        .add(new TargetModel(binding.providerId(), binding.providerModelId(),
                                model.getModelCode()));
            }
        }
        return targets;
    }

    private void applyOutcome(TargetModel target, ProbeOutcome outcome) {
        BindingHealthRegistry.Target registryTarget =
                new BindingHealthRegistry.Target(target.providerId(), target.providerModelId());
        if (outcome.ok()) {
            registry.recordSuccess(registryTarget, outcome.latencyMs());
        } else {
            registry.recordFailure(registryTarget, outcome.error());
        }
        persist(target, registryTarget);
    }

    /** 低频 upsert mux_binding_health（按 uk(provider_id, provider_model_id)）。 */
    private void persist(TargetModel target, BindingHealthRegistry.Target registryTarget) {
        BindingHealthRegistry.State state = registry.snapshot().get(registryTarget);
        if (state == null) {
            return;
        }
        BindingHealthEntity entity = healthDao.selectOne(new LambdaQueryWrapper<BindingHealthEntity>()
                .eq(BindingHealthEntity::getProviderId, target.providerId())
                .eq(BindingHealthEntity::getProviderModelId, target.providerModelId()));
        if (entity == null) {
            entity = new BindingHealthEntity();
            entity.setProviderId(target.providerId());
            entity.setProviderModelId(target.providerModelId());
        }
        entity.setModelCode(target.modelCode());
        entity.setHealthStatus(state.status().name());
        entity.setConsecutiveFailures(state.consecutiveFailures());
        entity.setLastProbeAt(LocalDateTime.now());
        entity.setLastLatencyMs(state.lastLatencyMs());
        entity.setLastError(state.lastError());
        if (entity.getProbeId() == null) {
            healthDao.insert(entity);
        } else {
            healthDao.updateById(entity);
        }
    }

    /** 单次探测：GET {baseUrl}/models，2xx 为成功。 */
    ProbeOutcome probe(ProviderEntity provider, String apiKey) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(provider.getBaseUrl().replaceAll("/+$", "") + "/models");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(probeTimeoutMs);
            connection.setReadTimeout(probeTimeoutMs);
            int status = connection.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (status >= 200 && status < 300) {
                return ProbeOutcome.ok(latency);
            }
            return ProbeOutcome.failed("HTTP " + status);
        } catch (IOException e) {
            return ProbeOutcome.failed(e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 探测结果。 */
    record ProbeOutcome(boolean ok, long latencyMs, String error) {
        static ProbeOutcome ok(long latencyMs) {
            return new ProbeOutcome(true, latencyMs, null);
        }

        static ProbeOutcome failed(String error) {
            return new ProbeOutcome(false, -1, error == null ? "unknown" : error);
        }
    }

    /** 探测目标 + 冗余 modelCode。 */
    private record TargetModel(String providerId, String providerModelId, String modelCode) {
    }
}
