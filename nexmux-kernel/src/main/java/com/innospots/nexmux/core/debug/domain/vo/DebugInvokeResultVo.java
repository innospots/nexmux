package com.innospots.nexmux.core.debug.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试调用结果（字段与调试台 UI 的 round/result 形状对齐）。
 *
 * @param requestId        关联请求日志 ID（mux_request_log.request_id）
 * @param status           HTTP 状态码
 * @param body             上游响应原文
 * @param latencyMs        耗时（毫秒）
 * @param reply            assistant 回复全文（解析自 choices[0].message.content）
 * @param tokensIn         输入 tokens（usage.prompt_tokens）
 * @param tokensOut        输出 tokens（usage.completion_tokens）
 * @param ttftMs           首 Token 耗时（非流式 = latencyMs）
 * @param routeGroup       命中路由组（路由首个标签；模型直连为空）
 * @param target           命中目标摘要（模型 · 供应商）
 * @param quotaPreview     配额扣减预览文案（客户端调试）
 * @param rateLimitResult  限流检查文案（客户端调试）
 */
@Schema(name = "DebugInvokeResultVo", description = "调试调用结果")
public record DebugInvokeResultVo(
        @Schema(description = "关联请求日志 ID")
        String requestId,
        @Schema(description = "HTTP 状态码", examples = {"200"})
        int status,
        @Schema(description = "上游响应原文")
        String body,
        @Schema(description = "耗时（毫秒）")
        int latencyMs,
        @Schema(description = "assistant 回复全文")
        String reply,
        @Schema(description = "输入 tokens")
        Long tokensIn,
        @Schema(description = "输出 tokens")
        Long tokensOut,
        @Schema(description = "首 Token 耗时（毫秒）")
        Integer ttftMs,
        @Schema(description = "命中路由组")
        String routeGroup,
        @Schema(description = "命中目标摘要")
        String target,
        @Schema(description = "配额扣减预览文案")
        String quotaPreview,
        @Schema(description = "限流检查文案")
        String rateLimitResult) {
}
