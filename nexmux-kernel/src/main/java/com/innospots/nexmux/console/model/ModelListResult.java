package com.innospots.nexmux.console.model;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Model 列表结果（/api/d/models UI 契约：{items,total,filters}；
 * counts/filters 为构成口径——全量统计，不随当前筛选变化）。
 *
 * @param items    当前页列表行
 * @param total    筛选后总数
 * @param pageNo   页码
 * @param pageSize 每页条数
 * @param pages    总页数
 * @param counts   构成口径计数
 * @param filters  筛选 chip 组
 */
@Schema(name = "ModelListResult", description = "Model 列表结果（UI 契约）")
public record ModelListResult(
        @Schema(description = "当前页列表行", required = true)
        List<ModelRecord> items,
        @Schema(description = "筛选后总数", required = true)
        long total,
        @Schema(description = "页码", required = true)
        long pageNo,
        @Schema(description = "每页条数", required = true)
        long pageSize,
        @Schema(description = "总页数", required = true)
        long pages,
        @Schema(description = "构成口径计数")
        Counts counts,
        @Schema(description = "筛选 chip 组")
        Filters filters) {

    /**
     * 构成口径计数。
     *
     * @param all       全量启用/总数
     * @param types     类型维度计数（LLM / Embedding / Image / Audio）
     * @param providers 提供方维度计数（providerCode → 模型数）
     */
    @Schema(name = "ModelListCounts", description = "构成口径计数")
    public record Counts(
            @Schema(description = "全量启用/总数")
            EnabledTotal all,
            @Schema(description = "类型维度计数")
            Map<String, Long> types,
            @Schema(description = "提供方维度计数")
            Map<String, Long> providers) {
    }

    /**
     * 筛选 chip 组。
     *
     * @param type      类型快筛
     * @param providers 提供方筛选
     */
    @Schema(name = "ModelListFilters", description = "筛选 chip 组")
    public record Filters(
            @Schema(description = "类型快筛")
            List<FilterChip> type,
            @Schema(description = "提供方筛选")
            List<FilterChip> providers) {
    }
}
