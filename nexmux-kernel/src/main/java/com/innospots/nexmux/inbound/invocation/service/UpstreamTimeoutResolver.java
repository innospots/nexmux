package com.innospots.nexmux.inbound.invocation.service;

import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import lombok.RequiredArgsConstructor;

/**
 * 上游超时解析：把 Provider 配置与服务设置合成为实际超时值。
 *
 * <p>读取超时回退链为 Provider.timeoutMs → 服务设置 requestTimeoutMs → 常量；
 * 连接超时取服务设置 upstreamConnectTimeoutMs。设置项近实时读取（带秒级 TTL 缓存），
 * 不走路由快照，便于运维即时调整。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see PlatformSettingService
 */
@RequiredArgsConstructor
public class UpstreamTimeoutResolver {

    /** 缺省读取超时（毫秒）。 */
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    /** 缺省连接超时（毫秒）。 */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;

    private final PlatformSettingService platformSettingService;

    /**
     * 解析读取超时。
     *
     * @param provider 供应商配置
     * @return 超时毫秒数
     */
    public int readTimeoutMillis(ProviderEntity provider) {
        if (provider.getTimeoutMs() != null && provider.getTimeoutMs() > 0) {
            return provider.getTimeoutMs();
        }
        Integer configured = settingInt("requestTimeoutMs");
        return configured != null && configured > 0 ? configured : DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * 解析连接超时。
     *
     * @return 超时毫秒数
     */
    public int connectTimeoutMillis() {
        Integer configured = settingInt("upstreamConnectTimeoutMs");
        return configured != null && configured > 0 ? configured : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    private Integer settingInt(String key) {
        try {
            String value = platformSettingService.getAll().get(key);
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
