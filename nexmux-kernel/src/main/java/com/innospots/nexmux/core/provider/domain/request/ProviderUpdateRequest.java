package com.innospots.nexmux.core.provider.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新模型服务提供方可编辑字段。
 *
 * @param displayName 显示名称
 * @param baseUrl     服务基址
 * @param region      区域
 * @param timeoutMs   超时（毫秒）
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
        Integer timeoutMs) {
}
