package com.innospots.nexmux.core.provider.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新模型服务提供方可编辑字段。
 *
 * @param displayName 显示名称
 * @param baseUrl     服务基址
 * @param region      区域
 * @param timeoutMs   超时（毫秒）
 * @param apiKey      上游 API Key（null/空 = 不修改；明文仅传输，库内按现有 ciphertext 列存放）
 * @param description 描述
 */
@Schema(name = "ProviderUpdateRequest", description = "更新模型服务提供方")
public record ProviderUpdateRequest(
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "服务基址")
        String baseUrl,
        @Schema(description = "区域")
        String region,
        @Schema(description = "超时（毫秒）")
        Integer timeoutMs,
        @Schema(description = "上游 API Key（空 = 不修改）")
        String apiKey,
        @Schema(description = "描述")
        String description) {
}
