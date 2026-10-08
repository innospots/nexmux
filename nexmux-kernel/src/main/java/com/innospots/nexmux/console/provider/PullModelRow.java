package com.innospots.nexmux.console.provider;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 拉取上游模型行（provider-detail 拉取弹层表格）。
 *
 * @param id    上游模型 ID
 * @param name  建议 Model Name（同上游 ID）
 * @param local 本地状态（已存在 / 新增）
 */
@Schema(name = "PullModelRow", description = "拉取上游模型行")
public record PullModelRow(
        @Schema(description = "上游模型 ID", required = true)
        String id,
        @Schema(description = "建议 Model Name", required = true)
        String name,
        @Schema(description = "本地状态", examples = {"新增"})
        String local) {
}
