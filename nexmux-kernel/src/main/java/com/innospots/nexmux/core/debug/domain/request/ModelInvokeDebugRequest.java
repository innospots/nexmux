package com.innospots.nexmux.core.debug.domain.request;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试直连模型调用（UI 契约：模型调试台发送字段）。
 *
 * @param modelCode    模型编码（/ 分隔）
 * @param systemPrompt 会话级系统提示词（可空）
 * @param text         用户消息文本
 * @param chatParams   模型参数（temperature / topP / maxTokens / reasoningEffort，可空）
 */
@Schema(name = "ModelInvokeDebugRequest", description = "调试模型调用")
public record ModelInvokeDebugRequest(
        @Schema(description = "模型编码", required = true)
        String modelCode,
        @Schema(description = "系统提示词")
        String systemPrompt,
        @Schema(description = "用户消息文本", required = true)
        String text,
        @Schema(description = "模型参数覆盖")
        Map<String, Object> chatParams) {
}
