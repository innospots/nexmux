package com.innospots.nexmux.core.model.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型配置概要（列表行；含卡片展示所需的规格与定价原文，整形在 BFF）。
 *
 * @param modelId            模型 ID
 * @param modelCode          模型编码
 * @param displayName        显示名称
 * @param status             状态
 * @param modelType          模型类型
 * @param contextLength      上下文窗口
 * @param maxOutputTokens    最大输出 tokens
 * @param primaryProviderId  主提供方 ID
 * @param pricingSpec        定价整包（JSON 文本）
 * @param extraBindings      扩展绑定（JSON 数组文本；卡片「供应商数」展示用）
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
        String status,
        @Schema(description = "模型类型", examples = {"LLM"})
        String modelType,
        @Schema(description = "上下文窗口")
        Long contextLength,
        @Schema(description = "最大输出 tokens")
        Long maxOutputTokens,
        @Schema(description = "主提供方 ID")
        String primaryProviderId,
        @Schema(description = "定价整包（JSON 文本）")
        String pricingSpec,
        @Schema(description = "扩展绑定（JSON 数组文本）")
        String extraBindings) {
}
