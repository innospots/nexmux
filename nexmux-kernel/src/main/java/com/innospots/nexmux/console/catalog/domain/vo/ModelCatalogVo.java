package com.innospots.nexmux.console.catalog.domain.vo;

import com.innospots.nexmux.console.shared.FilterChip;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 模型目录：本地已创建供应商可用的上游模型，附安装模型类型与供应商筛选。
 *
 * @param total            当前筛选结果行数
 * @param catalogTotal     供应商范围内可用模型总数
 * @param importable       可导入数量（尚未建模）
 * @param imported         已建模数量
 * @param items            当前筛选结果
 * @param typeFilters      安装模型类型筛选（含计数）
 * @param providerFilters  供应商筛选（含计数，仅本地已创建供应商）
 */
@Schema(name = "ModelCatalogVo", description = "模型目录")
public record ModelCatalogVo(
        @Schema(description = "当前筛选结果行数")
        int total,
        @Schema(description = "供应商范围内可用模型总数")
        int catalogTotal,
        @Schema(description = "可导入数量（尚未建模）")
        int importable,
        @Schema(description = "已建模数量")
        int imported,
        @Schema(description = "当前筛选结果", required = true)
        List<ModelCatalogRowVo> items,
        @Schema(description = "安装模型类型筛选")
        List<FilterChip> typeFilters,
        @Schema(description = "供应商筛选")
        List<FilterChip> providerFilters) {
}
