package com.innospots.nexmux.core.debug.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 调试直连模型调用。
 *
 * @param modelId 模型 ID
 * @param payload 请求体 JSON 字符串
 */
@Schema(name = "ModelInvokeDebugRequest", description = "调试模型调用")
public record ModelInvokeDebugRequest(
        @Schema(description = "模型 ID", required = true)
        String modelId,
        @Schema(description = "请求体 JSON 字符串", required = true)
        String payload) {
}
