package com.innospots.nexmux.core.access.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 轮换 AppKey 结果。
 *
 * @param clientId         客户端 ID
 * @param appKeyPlaintext  新明文 AppKey
 * @param appKeyVersion    AppKey 版本号
 */
@Schema(name = "ClientRotateResultVo", description = "轮换 AppKey 结果")
public record ClientRotateResultVo(
        @Schema(description = "客户端 ID", required = true)
        String clientId,
        @Schema(description = "新明文 AppKey", required = true)
        String appKeyPlaintext,
        @Schema(description = "AppKey 版本号", required = true)
        int appKeyVersion) {
}
