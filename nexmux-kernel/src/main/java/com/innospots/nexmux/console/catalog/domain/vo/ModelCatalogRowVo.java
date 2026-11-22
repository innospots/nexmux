package com.innospots.nexmux.console.catalog.domain.vo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型目录行：本地已创建的供应商在模板目录中可用的上游模型。
 *
 * @param providerCode  本地供应商编码
 * @param providerName  本地供应商名称
 * @param presetCode    该供应商对应的模板编码
 * @param id            上游模型 ID
 * @param name          模型名称（按请求语言解析）
 * @param description   模型说明（按请求语言解析）
 * @param modelType     安装模型类型（LLM / Image / Audio / Embedding）
 * @param contextLength 上下文窗口
 * @param maxOutputTokens 最大输出 tokens
 * @param pricingLabel  定价摘要（输入 / 输出，每百万 tokens）
 * @param existsLocally 本地是否已建模
 */
@Schema(name = "ModelCatalogRowVo", description = "模型目录行")
public record ModelCatalogRowVo(
        @Schema(description = "本地供应商编码", required = true)
        String providerCode,
        @Schema(description = "本地供应商名称", required = true)
        String providerName,
        @Schema(description = "模板编码")
        String presetCode,
        @Schema(description = "上游模型 ID", required = true)
        String id,
        @Schema(description = "模型名称", required = true)
        String name,
        @Schema(description = "模型说明")
        String description,
        @Schema(description = "安装模型类型", examples = {"LLM"})
        String modelType,
        @Schema(description = "上下文窗口")
        Long contextLength,
        @Schema(description = "最大输出 tokens")
        Long maxOutputTokens,
        @Schema(description = "定价摘要")
        String pricingLabel,
        @Schema(description = "本地是否已建模")
        boolean existsLocally) {
}
