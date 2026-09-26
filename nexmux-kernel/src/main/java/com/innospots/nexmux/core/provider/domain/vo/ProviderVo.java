package com.innospots.nexmux.core.provider.domain.vo;

public record ProviderVo(
        String providerId,
        String providerCode,
        String displayName,
        String providerType,
        String defaultProtocol,
        String vendorCode,
        String sourceVendorCode,
        String iconUri,
        String baseUrl,
        String region,
        Integer timeoutMs,
        String status,
        boolean hasCredential) {
}
