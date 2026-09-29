package com.innospots.nexmux.core.access.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建接入客户端。
 *
 * @param clientCode  客户端编码
 * @param displayName 显示名称
 */
@Schema(name = "ClientCreateRequest", description = "创建接入客户端")
public record ClientCreateRequest(
        @Schema(description = "客户端编码", required = true, examples = {"mobile-app"})
        String clientCode,
        @Schema(description = "显示名称", required = true)
        String displayName) {
}
