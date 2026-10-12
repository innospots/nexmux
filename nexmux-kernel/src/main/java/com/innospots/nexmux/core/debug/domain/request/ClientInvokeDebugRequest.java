package com.innospots.nexmux.core.debug.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试经客户端与路由的调用（UI 契约：客户端调试台发送字段）。
 *
 * @param clientCode 客户端编码
 * @param routeCode  路由编码
 * @param text       用户消息文本
 */
@Schema(name = "ClientInvokeDebugRequest", description = "调试客户端调用")
public record ClientInvokeDebugRequest(
        @Schema(description = "客户端编码", required = true)
        String clientCode,
        @Schema(description = "路由编码", required = true)
        String routeCode,
        @Schema(description = "用户消息文本", required = true)
        String text) {
}
