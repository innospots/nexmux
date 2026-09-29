package com.innospots.nexmux.core.debug.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试调用结果。
 *
 * @param requestId  关联请求日志 ID
 * @param auditClass 审计分类
 * @param httpStatus HTTP 状态码
 * @param body       响应体
 * @param latencyMs  耗时（毫秒）
 */
@Schema(name = "DebugInvokeResultVo", description = "调试调用结果")
public record DebugInvokeResultVo(
        @Schema(description = "关联请求日志 ID")
        String requestId,
        @Schema(description = "审计分类")
        String auditClass,
        @Schema(description = "HTTP 状态码", examples = {"200"})
        int httpStatus,
        @Schema(description = "响应体")
        String body,
        @Schema(description = "耗时（毫秒）")
        int latencyMs) {
}
