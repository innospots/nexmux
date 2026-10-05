package com.innospots.nexmux.core.model.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型详情（按稳定业务键查询；绑定与定价以 JSON 原文透传，整形在 BFF）。
 *
 * @param modelId                     模型 ID
 * @param modelCode                   模型编码（稳定业务键）
 * @param displayName                 显示名称
 * @param modelType                   模型类型
 * @param status                      状态
 * @param iconUri                     图标 URI
 * @param releaseDate                 发布日期
 * @param contextLength               上下文窗口
 * @param maxOutputTokens             最大输出 tokens
 * @param inputModalities             输入模态（逗号分隔）
 * @param outputModalities            输出模态（逗号分隔）
 * @param capabilityTags              能力标签（JSON 数组文本）
 * @param tags                        标签（JSON 数组文本）
 * @param description                 描述
 * @param metadataSpec                元数据（JSON 文本）
 * @param defaultParams               默认参数（JSON 文本）
 * @param primaryProviderId           主提供方 ID
 * @param primaryProviderModelId      主绑定上游模型 ID
 * @param primaryUpstreamProtocol     主绑定协议覆盖
 * @param primaryBindingWeight        主绑定权重
 * @param primaryCostMultiplier       主绑定成本倍率 C
 * @param primaryReferenceMultiplier  主绑定参考倍率 M
 * @param primaryBindingEnabled       主绑定启用态
 * @param extraBindings               扩展绑定（JSON 数组文本，ModelBindingItem 结构）
 * @param pricingSpec                 定价整包（JSON 文本）
 * @param createdAt                   创建时间
 * @param updatedAt                   更新时间
 * @param updatedBy                   最近操作人
 */
@Schema(name = "ModelDetailVo", description = "模型详情")
public record ModelDetailVo(
        @Schema(description = "模型 ID", required = true)
        String modelId,
        @Schema(description = "模型编码", required = true)
        String modelCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "模型类型")
        String modelType,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "图标 URI")
        String iconUri,
        @Schema(description = "发布日期", format = "date")
        LocalDate releaseDate,
        @Schema(description = "上下文窗口")
        Long contextLength,
        @Schema(description = "最大输出 tokens")
        Long maxOutputTokens,
        @Schema(description = "输入模态（逗号分隔）")
        String inputModalities,
        @Schema(description = "输出模态（逗号分隔）")
        String outputModalities,
        @Schema(description = "能力标签（JSON 数组文本）")
        String capabilityTags,
        @Schema(description = "标签（JSON 数组文本）")
        String tags,
        @Schema(description = "描述")
        String description,
        @Schema(description = "元数据（JSON 文本）")
        String metadataSpec,
        @Schema(description = "默认参数（JSON 文本）")
        String defaultParams,
        @Schema(description = "主提供方 ID")
        String primaryProviderId,
        @Schema(description = "主绑定上游模型 ID")
        String primaryProviderModelId,
        @Schema(description = "主绑定协议覆盖")
        String primaryUpstreamProtocol,
        @Schema(description = "主绑定权重")
        Integer primaryBindingWeight,
        @Schema(description = "主绑定成本倍率 C")
        BigDecimal primaryCostMultiplier,
        @Schema(description = "主绑定参考倍率 M")
        BigDecimal primaryReferenceMultiplier,
        @Schema(description = "主绑定启用态")
        Boolean primaryBindingEnabled,
        @Schema(description = "扩展绑定（JSON 数组文本）")
        String extraBindings,
        @Schema(description = "定价整包（JSON 文本）")
        String pricingSpec,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt,
        @Schema(description = "更新时间", format = "date-time")
        LocalDateTime updatedAt,
        @Schema(description = "最近操作人")
        String updatedBy) {
}
