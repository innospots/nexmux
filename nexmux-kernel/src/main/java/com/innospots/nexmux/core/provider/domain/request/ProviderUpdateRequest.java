package com.innospots.nexmux.core.provider.domain.request;

public record ProviderUpdateRequest(String displayName, String baseUrl, String region, Integer timeoutMs) {
}
