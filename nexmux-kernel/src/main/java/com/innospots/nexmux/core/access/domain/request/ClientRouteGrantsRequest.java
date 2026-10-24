package com.innospots.nexmux.core.access.domain.request;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新客户端可访问的路由列表。
 *
 * @param routeIds 授权的路由 ID 列表
 */
@Schema(name = "ClientRouteGrantsRequest", description = "客户端路由授权")
public record ClientRouteGrantsRequest(
        @Schema(description = "授权的路由 ID 列表")
        List<String> routeIds) {
    public ClientRouteGrantsRequest {
        routeIds = routeIds == null ? List.of() : List.copyOf(routeIds);
    }
}
