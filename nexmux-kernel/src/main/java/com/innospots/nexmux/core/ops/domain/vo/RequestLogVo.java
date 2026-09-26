package com.innospots.nexmux.core.ops.domain.vo;

import java.time.LocalDateTime;

public record RequestLogVo(
        String requestId,
        String clientId,
        String routeId,
        String modelCode,
        String status,
        Long latencyMs,
        String errorCode,
        String routeTrace,
        LocalDateTime requestedAt) {
}
