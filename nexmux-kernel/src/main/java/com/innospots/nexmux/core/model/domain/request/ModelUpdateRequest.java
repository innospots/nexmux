package com.innospots.nexmux.core.model.domain.request;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新模型请求（可编辑字段；绑定与定价另由专门流程维护）。
 *
 * @param displayName      显示名称
 * @param description      描述
 * @param contextLength    上下文窗口
 * @param maxOutputTokens  最大输出 tokens
 * @param inputModalities  输入模态
 * @param outputModalities 输出模态
 * @param tags             标签
 * @param metadataSpec    元数据 JSON
 * @param defaultParams   默认执行参数 JSON
 * @param capabilityTags  能力标签 JSON 数组
 */
@Schema(name = "ModelUpdateRequest", description = "更新模型请求")
public record ModelUpdateRequest(
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "描述")
        String description,
        @Schema(description = "上下文窗口")
        Long contextLength,
        @Schema(description = "最大输出 tokens")
        Long maxOutputTokens,
        @Schema(description = "输入模态")
        List<String> inputModalities,
        @Schema(description = "输出模态")
        List<String> outputModalities,
        @Schema(description = "标签")
        List<String> tags,
        @Schema(description = "元数据 JSON")
        String metadataSpec,
        @Schema(description = "默认执行参数 JSON")
        String defaultParams,
        @Schema(description = "能力标签 JSON 数组")
        String capabilityTags,
        @Schema(description = "主 Binding 权重")
        Integer primaryBindingWeight,
        @Schema(description = "主 Binding 成本倍率 C")
        BigDecimal primaryCostMultiplier,
        @Schema(description = "主 Binding 内部参考倍率 M")
        BigDecimal primaryReferenceMultiplier,
        @Schema(description = "主 Binding 是否启用")
        Boolean primaryBindingEnabled,
        @Schema(description = "扩展 Binding JSON 数组")
        String extraBindings,
        @Schema(description = "定价 JSON")
        String pricingSpec) {
}
