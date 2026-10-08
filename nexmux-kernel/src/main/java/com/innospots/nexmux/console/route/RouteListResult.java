package com.innospots.nexmux.console.route;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Route 列表结果（/api/d/routes UI 契约：{items,total,filters}；
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
@Schema(name = "RouteListResult", description = "Route 列表结果（UI 契约）")
public record RouteListResult(
        List<RouteRecord> items,
        long total,
        long pageNo,
        long pageSize,
        long pages,
        Counts counts,
        Filters filters) {

    /**
     * 构成口径计数。
     *
     * @param all    全量启用/总数
     * @param status 状态维度计数
     * @param types  类型维度计数（single / pool / conditional → 启用/总数）
     * @param tags   标签维度计数（键以 _ 替代 -，与原 BFF 口径一致）
     */
    @Schema(name = "RouteListCounts", description = "构成口径计数")
    public record Counts(
            EnabledTotal all,
            StatusCounts status,
            Map<String, EnabledTotal> types,
            Map<String, Long> tags) {
    }

    /**
     * 状态维度计数。
     *
     * @param enabled  已启用数
     * @param disabled 已停用数
     * @param draft    草稿数
     */
    @Schema(name = "RouteStatusCounts", description = "状态维度计数")
    public record StatusCounts(
            long enabled,
            long disabled,
            long draft) {
    }

    /**
     * 筛选 chip 组。
     *
     * @param status 状态快筛
     * @param type   类型快筛
     * @param tags   标签筛选
     */
    @Schema(name = "RouteListFilters", description = "筛选 chip 组")
    public record Filters(
            List<FilterChip> status,
            List<FilterChip> type,
            List<FilterChip> tags) {
    }
}
