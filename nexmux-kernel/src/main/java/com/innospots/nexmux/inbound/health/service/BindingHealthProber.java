package com.innospots.nexmux.inbound.health.service;

import com.innospots.nexmux.core.health.service.BindingHealthRegistry;
import com.innospots.nexmux.core.health.service.BindingHealthService;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.shared.security.CredentialCipher;
import com.innospots.nexmux.inbound.catalog.service.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.invocation.service.ModelBindingResolver;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Binding 主动探活：按周期对每个启用的供应商发起 {@code GET {baseUrl}/models}（OpenAI 兼容）。
 *
 * <p>结果由 {@link BindingHealthService} 写入状态机并落库 {@code mux_binding_health}；
 * 本类只负责出站探测（网络副作用），不直接访问 DAO，也不持有健康状态。
 * 探测流量不落 {@code mux_request_log}。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see BindingHealthService
 * @see RuntimeCatalogCache
 */
@Slf4j
@RequiredArgsConstructor
public final class BindingHealthProber {

    private final RuntimeCatalogCache runtimeCatalogCache;
    private final BindingHealthService bindingHealthService;
    private final CredentialCipher credentialCipher;
    private final int probeTimeoutMillis;

    /**
     * 全量探测一轮；单供应商失败不影响其他。
     *
     * @return 实际探测的供应商数
     */
    public int probeAll() {
        int probed = 0;
        Map<String, List<TargetModel>> targetsByProvider = collectTargets();
        for (Map.Entry<String, List<TargetModel>> entry : targetsByProvider.entrySet()) {
            String providerId = entry.getKey();
            Optional<ProviderEntity> provider = runtimeCatalogCache.findProvider(providerId);
            if (provider.isEmpty() || !"ENABLED".equals(provider.get().getStatus())) {
                continue;
            }
            Optional<ProviderCredentialEntity> credential = runtimeCatalogCache.findCredential(providerId);
            if (credential.isEmpty() || credential.get().getCiphertext() == null
                    || credential.get().getCiphertext().isBlank()) {
                continue;
            }
            probed++;
            ProbeOutcome outcome = probe(provider.get(),
                    credentialCipher.decrypt(credential.get().getCiphertext()).trim());
            for (TargetModel target : entry.getValue()) {
                bindingHealthService.recordProbe(
                        new BindingHealthRegistry.Target(target.providerId(), target.providerModelId()),
                        target.modelCode(),
                        outcome.ok(),
                        outcome.latencyMillis(),
                        outcome.error());
            }
        }
        log.debug("binding health probe round finished: probed {} provider(s)", probed);
        return probed;
    }

    /** 探测目标收集：启用模型的全部启用 Binding 按供应商分组。 */
    private Map<String, List<TargetModel>> collectTargets() {
        Map<String, List<TargetModel>> targets = new LinkedHashMap<>();
        for (ModelEntity model : runtimeCatalogCache.models()) {
            if (!"ENABLED".equals(model.getStatus())) {
                continue;
            }
            for (ModelBindingResolver.ResolvedBinding binding : ModelBindingResolver.bindingsOf(model)) {
                targets.computeIfAbsent(binding.providerId(), key -> new ArrayList<>())
                        .add(new TargetModel(binding.providerId(), binding.providerModelId(),
                                model.getModelCode()));
            }
        }
        return targets;
    }

    /** 单次探测：GET {baseUrl}/models，2xx 为成功（包可见供单测替换）。 */
    ProbeOutcome probe(ProviderEntity provider, String apiKey) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(provider.getBaseUrl().replaceAll("/+$", "") + "/models");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(probeTimeoutMillis);
            connection.setReadTimeout(probeTimeoutMillis);
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

    /**
     * 探测结果。
     *
     * @param ok           是否成功
     * @param latencyMillis 延迟（毫秒；失败为 -1）
     * @param error        失败原因（成功为 {@code null}）
     */
    record ProbeOutcome(boolean ok, long latencyMillis, String error) {

        static ProbeOutcome ok(long latencyMillis) {
            return new ProbeOutcome(true, latencyMillis, null);
        }

        static ProbeOutcome failed(String error) {
            return new ProbeOutcome(false, -1, error == null ? "unknown" : error);
        }
    }

    /** 探测目标 + 冗余模型编码。 */
    private record TargetModel(String providerId, String providerModelId, String modelCode) {
    }
}
