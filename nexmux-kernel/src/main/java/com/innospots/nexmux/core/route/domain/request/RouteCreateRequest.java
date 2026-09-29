package com.innospots.nexmux.core.route.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建路由。
 *
 * @param alias       路由别名
 * @param displayName 显示名称
 */
@Schema(name = "RouteCreateRequest", description = "创建路由")
public record RouteCreateRequest(
        @Schema(description = "路由别名", required = true, examples = {"chat-default"})
        String alias,
        @Schema(description = "显示名称", required = true)
        String displayName) {
}
