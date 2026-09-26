package com.innospots.nexmux.core.access.domain.vo;

import java.util.List;

public record ClientVo(
        String clientId,
        String clientCode,
        String displayName,
        String status,
        List<String> authorizedRouteIds,
        String quotaSpec,
        String rateLimitSpec,
        int appKeyVersion) {
}
