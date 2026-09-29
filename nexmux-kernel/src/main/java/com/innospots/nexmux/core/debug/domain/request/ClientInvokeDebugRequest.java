package com.innospots.nexmux.core.debug.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试经客户端与路由的调用。
 *
 * @param clientId   客户端 ID
 * @param routeAlias 路由别名
 * @param payload    请求体 JSON 字符串
 */
@Schema(name = "ClientInvokeDebugRequest", description = "调试客户端调用")
public record ClientInvokeDebugRequest(
        @Schema(description = "客户端 ID", required = true)
        String clientId,
        @Schema(description = "路由别名", required = true)
        String routeAlias,
        @Schema(description = "请求体 JSON 字符串", required = true)
        String payload) {
}
