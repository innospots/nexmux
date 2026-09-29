package com.innospots.nexmux.core.model.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型配置概要。
 *
 * @param modelId     模型 ID
 * @param modelCode   模型编码
 * @param displayName 显示名称
 * @param status      状态
 */
@Schema(name = "ModelVo", description = "模型配置概要")
public record ModelVo(
        @Schema(description = "模型 ID", required = true)
        String modelId,
        @Schema(description = "模型编码", required = true)
        String modelCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status) {
}
