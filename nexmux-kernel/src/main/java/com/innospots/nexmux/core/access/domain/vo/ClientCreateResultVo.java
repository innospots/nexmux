package com.innospots.nexmux.core.access.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建客户端结果（含一次性明文 AppKey）。
 *
 * @param client           客户端概要
 * @param appKeyPlaintext  明文 AppKey（仅创建时返回一次）
 */
@Schema(name = "ClientCreateResultVo", description = "创建客户端结果")
public record ClientCreateResultVo(
        @Schema(description = "客户端概要", required = true)
        ClientVo client,
        @Schema(description = "明文 AppKey（仅创建时返回一次）", required = true)
        String appKeyPlaintext) {
}
