package com.innospots.nexmux.core.access.domain.request;

import java.util.List;

public record ClientRouteGrantsRequest(List<String> routeIds) {
    public ClientRouteGrantsRequest {
        routeIds = routeIds == null ? List.of() : List.copyOf(routeIds);
    }
}
