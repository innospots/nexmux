package com.innospots.nexmux.core.route.domain.request;

import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新路由草稿。
 *
 * @param displayName 显示名称
 * @param routeSpec   路由规格
 */
@Schema(name = "RouteUpdateRequest", description = "更新路由草稿")
public record RouteUpdateRequest(
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "路由规格")
        RouteSpec routeSpec) {
}
