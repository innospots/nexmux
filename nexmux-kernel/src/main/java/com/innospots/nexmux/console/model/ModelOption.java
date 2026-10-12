package com.innospots.nexmux.console.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Model 下拉选项（路由编辑「添加模型」Select）。
 *
 * @param label 展示文案（displayName（modelCode））
 * @param value 选项值（modelCode，后端 / 分隔形式，与 RouteCandidate.modelCode 一致）
 */
@Schema(name = "ModelOption", description = "Model 下拉选项")
public record ModelOption(
        @Schema(description = "展示文案", required = true)
        String label,
        @Schema(description = "选项值（模型编码，/ 分隔）", required = true)
        String value) {
}
