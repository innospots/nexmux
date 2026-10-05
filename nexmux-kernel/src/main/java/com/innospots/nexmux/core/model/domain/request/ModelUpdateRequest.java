package com.innospots.nexmux.core.model.domain.request;

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
        List<String> tags) {
}
