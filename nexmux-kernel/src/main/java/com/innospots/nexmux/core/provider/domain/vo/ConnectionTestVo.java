package com.innospots.nexmux.core.provider.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 提供方连通性测试结果。
 *
 * @param ok        是否连通
 * @param latencyMs 连接耗时（毫秒）
 * @param detail    结果说明（失败原因或可达描述）
 */
@Schema(name = "ConnectionTestVo", description = "提供方连通性测试结果")
public record ConnectionTestVo(
        @Schema(description = "是否连通", required = true)
        boolean ok,
        @Schema(description = "连接耗时（毫秒）", required = true)
        long latencyMs,
        @Schema(description = "结果说明")
        String detail) {
}
