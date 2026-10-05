package com.innospots.nexmux.core.provider.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 提供方下的模型行（详情页「模型」表）。
 *
 * @param modelCode       逻辑模型编码
 * @param displayName     显示名称
 * @param modelType       模型类型（LLM / Embedding / Image / Audio）
 * @param providerModelId 上游模型 ID
 * @param status          状态（ENABLED / DISABLED）
 */
@Schema(name = "ProviderModelRowVo", description = "提供方下的模型行")
public record ProviderModelRowVo(
        @Schema(description = "逻辑模型编码", required = true)
        String modelCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "模型类型")
        String modelType,
        @Schema(description = "上游模型 ID")
        String providerModelId,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status) {
}
