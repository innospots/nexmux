package com.innospots.nexmux.core.model.domain.request;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建模型请求。
 *
 * @param modelCode              模型编码（稳定业务键）
 * @param displayName            显示名称
 * @param modelType              模型类型（LLM / Embedding / Image / Audio）
 * @param primaryProviderCode    主提供方编码（按稳定业务键关联）
 * @param primaryProviderModelId 主绑定上游模型 ID
 * @param primaryUpstreamProtocol 主绑定协议覆盖（空 = 继承 Provider 默认协议）
 * @param contextLength          上下文窗口
 * @param maxOutputTokens        最大输出 tokens
 * @param inputModalities        输入模态（text / image / video / audio）
 * @param outputModalities       输出模态
 * @param tags                   标签
 * @param description            描述
 * @param metadataSpec          元数据 JSON
 * @param defaultParams         默认执行参数 JSON
 * @param capabilityTags        能力标签 JSON 数组
 */
@Schema(name = "ModelCreateRequest", description = "创建模型请求")
public record ModelCreateRequest(
        @Schema(description = "模型编码", required = true, examples = {"gpt-4o"})
        String modelCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "模型类型", examples = {"LLM"})
        String modelType,
        @Schema(description = "主提供方编码", required = true, examples = {"dashscope"})
        String primaryProviderCode,
        @Schema(description = "主绑定上游模型 ID", examples = {"qwen3.5-flash"})
        String primaryProviderModelId,
        @Schema(description = "主绑定协议覆盖")
        String primaryUpstreamProtocol,
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
        @Schema(description = "描述")
        String description,
        @Schema(description = "元数据 JSON")
        String metadataSpec,
        @Schema(description = "默认执行参数 JSON")
        String defaultParams,
        @Schema(description = "能力标签 JSON 数组")
        String capabilityTags,
        @Schema(description = "主 Binding 权重，默认 100")
        Integer primaryBindingWeight,
        @Schema(description = "主 Binding 成本倍率 C，默认 1")
        BigDecimal primaryCostMultiplier,
        @Schema(description = "主 Binding 内部参考倍率 M，默认 1")
        BigDecimal primaryReferenceMultiplier,
        @Schema(description = "主 Binding 是否启用，默认 true")
        Boolean primaryBindingEnabled,
        @Schema(description = "扩展 Binding JSON 数组")
        String extraBindings,
        @Schema(description = "定价 JSON")
        String pricingSpec) {
}
