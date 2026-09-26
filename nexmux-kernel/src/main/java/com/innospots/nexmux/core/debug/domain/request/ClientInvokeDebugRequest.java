package com.innospots.nexmux.core.debug.domain.request;

public record ClientInvokeDebugRequest(String clientId, String routeAlias, String payload) {
}
