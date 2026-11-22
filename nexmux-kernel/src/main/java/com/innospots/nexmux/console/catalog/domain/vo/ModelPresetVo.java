package com.innospots.nexmux.console.catalog.domain.vo;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型模板条目（供应商模板下的可选模型）。
 *
 * <p>名称与说明在装配阶段按请求语言解析为单字符串。</p>
 *
 * @param id               上游模型 ID
 * @param name             建议模型名称
 * @param description      模型说明
 * @param modelType        模型类型（LLM / Embedding / Image / Audio）
 * @param contextLength    上下文窗口
 * @param maxOutputTokens  最大输出 tokens
 * @param inputModalities  输入模态
 * @param outputModalities 输出模态
 * @param capabilityTags   能力标签
 * @param existsLocally    本地是否已有该上游模型
 */
@Schema(name = "ModelPresetVo", description = "模型模板条目")
public record ModelPresetVo(
        @Schema(description = "上游模型 ID", required = true)
        String id,
        @Schema(description = "建议模型名称", required = true)
        String name,
        @Schema(description = "模型说明")
        String description,
        @Schema(description = "模型类型", examples = {"LLM"})
        String modelType,
        @Schema(description = "上下文窗口")
        Long contextLength,
        @Schema(description = "最大输出 tokens")
        Long maxOutputTokens,
        @Schema(description = "输入模态")
        List<String> inputModalities,
        @Schema(description = "输出模态")
        List<String> outputModalities,
        @Schema(description = "能力标签")
        List<String> capabilityTags,
        @Schema(description = "本地是否已存在")
        boolean existsLocally) {
}
