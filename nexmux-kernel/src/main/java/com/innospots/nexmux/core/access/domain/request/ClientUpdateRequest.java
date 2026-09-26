package com.innospots.nexmux.core.access.domain.request;

public record ClientUpdateRequest(String displayName, String quotaSpec, String rateLimitSpec) {
}
