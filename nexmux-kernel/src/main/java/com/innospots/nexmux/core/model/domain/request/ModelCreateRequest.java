package com.innospots.nexmux.core.model.domain.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建模型配置。
 *
 * @param modelCode   模型编码
 * @param displayName 显示名称
 * @param modelType   模型类型
 */
@Schema(name = "ModelCreateRequest", description = "创建模型配置")
public record ModelCreateRequest(
        @Schema(description = "模型编码", required = true, examples = {"gpt-4o"})
        String modelCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "模型类型", examples = {"CHAT"})
        String modelType) {
}
