package com.innospots.nexmux.console.catalog.domain.vo;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商模板目录。
 *
 * @param apiVersion    预设资源版本
 * @param updatedAt     预设资源生成时间
 * @param providerCount 模板总数
 * @param modelCount    模板内模型总数
 * @param importedCount 已导入的模板数
 * @param providers     模板条目
 */
@Schema(name = "ProviderPresetCatalogVo", description = "供应商模板目录")
public record ProviderPresetCatalogVo(
        @Schema(description = "预设资源版本", examples = {"nexmux.provider-presets/v1"})
        String apiVersion,
        @Schema(description = "预设资源生成时间")
        String updatedAt,
        @Schema(description = "模板总数")
        int providerCount,
        @Schema(description = "模板内模型总数")
        int modelCount,
        @Schema(description = "已导入模板数")
        int importedCount,
        @Schema(description = "模板条目", required = true)
        List<ProviderPresetVo> providers) {
}
