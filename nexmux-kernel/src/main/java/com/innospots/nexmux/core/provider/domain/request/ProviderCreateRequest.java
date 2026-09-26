package com.innospots.nexmux.core.provider.domain.request;

public record ProviderCreateRequest(
        String providerCode,
        String displayName,
        String providerType,
        String defaultProtocol,
        String vendorCode,
        String sourceVendorCode,
        String iconUri,
        String baseUrl,
        String region,
        Integer timeoutMs) {
}
