package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.shared.security.AppKeySupport;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.runtime.RuntimeCatalogCache;
import com.innospots.nexus.base.exception.NexusException;
import java.time.LocalDateTime;

/**
 * AppKey 鉴权（nexmux-runtime-design §8）：从目录缓存遍历客户端，以
 * {@link AppKeySupport#hash(String, String)}（盐 + SHA-256）匹配；
 * 缺失 / 不匹配 / 客户端停用 / 已过期一律 401 {@code APP_KEY_INVALID}
 * （不区分原因，避免凭证探测）。P0 客户端规模下线性匹配；缓存由快照调度刷新。
 */
public final class AppKeyVerifier {

    private final RuntimeCatalogCache catalogCache;

    public AppKeyVerifier(RuntimeCatalogCache catalogCache) {
        this.catalogCache = catalogCache;
    }

    /**
     * 校验并返回客户端。
     *
     * @param presentedKey 请求头明文（Bearer 或 X-API-Key 值，可含 {@code gw-sk-} 前缀）
     * @throws NexusException 401 {@code NMX040002}
     */
    public ClientEntity authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            throw NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "missing app key");
        }
        String plain = presentedKey.trim();
        for (ClientEntity client : catalogCache.clients()) {
            if (client.getAppKeyHash() == null || client.getAppKeySalt() == null) {
                continue;
            }
            if (AppKeySupport.hash(plain, client.getAppKeySalt()).equals(client.getAppKeyHash())) {
                if (!"ENABLED".equals(client.getStatus())) {
                    throw NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "client is disabled");
                }
                if (client.getExpiresAt() != null && client.getExpiresAt().isBefore(LocalDateTime.now())) {
                    throw NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "client is expired");
                }
                return client;
            }
        }
        throw NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "invalid app key");
    }
}
