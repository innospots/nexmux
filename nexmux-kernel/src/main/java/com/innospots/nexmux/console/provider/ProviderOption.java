package com.innospots.nexmux.console.provider;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Provider 下拉选项（模型新建/编辑页 Select）。
 *
 * @param label 展示文案（displayName）
 * @param value 选项值（providerCode）
 */
@Schema(name = "ProviderOption", description = "Provider 下拉选项")
public record ProviderOption(
        @Schema(description = "展示文案", required = true)
        String label,
        @Schema(description = "选项值", required = true)
        String value) {
}
