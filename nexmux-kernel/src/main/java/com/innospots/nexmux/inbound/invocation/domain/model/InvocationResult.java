package com.innospots.nexmux.inbound.invocation.domain.model;

/**
 * 一次调用结果。
 *
 * @param httpStatus 返回给接入方的 HTTP 状态
 * @param body       响应体（上游原始或聚合后的 JSON 文本）
 * @param latencyMs  端到端延迟（毫秒）
 * @param ttftMs     首字节延迟（毫秒；-1 表示无内容输出）
 * @param tokensIn   输入 tokens（可空）
 * @param tokensOut  输出 tokens（可空）
 * @param context    解析上下文（未过关口时为 {@code null}）
 * @author Smars
 * @date 2026/11/22
 * @see InvocationContext
 */
public record InvocationResult(
        int httpStatus,
        String body,
        long latencyMs,
        long ttftMs,
        Long tokensIn,
        Long tokensOut,
        InvocationContext context) {
}
