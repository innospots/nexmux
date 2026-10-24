package com.innospots.nexmux.console.access;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * ClientTemplate 列表结果（/api/d/client-templates UI 契约：{items,total,filters}；
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
@Schema(name = "ClientTemplateListResult", description = "ClientTemplate 列表结果（UI 契约）")
public record ClientTemplateListResult(
        List<ClientTemplateRecord> items,
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
     * @param tags   标签维度计数（键以 _ 替代 -）
     */
    @Schema(name = "ClientTemplateListCounts", description = "构成口径计数")
    public record Counts(
            EnabledTotal all,
            StatusCounts status,
            Map<String, Long> tags) {
    }

    /**
     * 状态维度计数。
     *
     * @param enabled  已启用数
     * @param disabled 已停用数
     */
    @Schema(name = "ClientTemplateStatusCounts", description = "状态维度计数")
    public record StatusCounts(
            long enabled,
            long disabled) {
    }

    /**
     * 筛选 chip 组。
     *
     * @param status 状态快筛
     * @param tags   标签筛选
     */
    @Schema(name = "ClientTemplateListFilters", description = "筛选 chip 组")
    public record Filters(
            List<FilterChip> status,
            List<FilterChip> tags) {
    }
}
