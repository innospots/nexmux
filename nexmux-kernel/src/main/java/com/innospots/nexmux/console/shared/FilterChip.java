package com.innospots.nexmux.console.shared;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 列表页筛选 chip（UI 契约：/api/d 列表接口 filters 数组元素）。
 *
 * @param value 选项值（回传查询参数）
 * @param text  展示文案（含构成口径计数）
 * @param dot   圆点色（可空，前端按主题色渲染）
 */
@Schema(name = "FilterChip", description = "列表页筛选 chip")
public record FilterChip(
        @Schema(description = "选项值", required = true)
        String value,
        @Schema(description = "展示文案", required = true)
        String text,
        @Schema(description = "圆点色")
        String dot) {
}
