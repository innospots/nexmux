package com.innospots.nexmux.core.debug.domain.vo;

public record DebugInvokeResultVo(String requestId, String auditClass, int httpStatus, String body, int latencyMs) {
}
