package com.innospots.nexmux.console.provider;

import com.innospots.nexmux.console.shared.EnabledTotal;
import com.innospots.nexmux.console.shared.FilterChip;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Provider 列表结果（/api/d/providers UI 契约：{items,total,filters}；
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
@Schema(name = "ProviderListResult", description = "Provider 列表结果（UI 契约）")
public record ProviderListResult(
        @Schema(description = "当前页列表行", required = true)
        List<ProviderRecord> items,
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
     * @param all    全量启用/总数
     * @param status 状态维度计数
     * @param types  厂商族维度计数（vendorCode → 启用/总数）
     */
    @Schema(name = "ProviderListCounts", description = "构成口径计数")
    public record Counts(
            @Schema(description = "全量启用/总数")
            EnabledTotal all,
            @Schema(description = "状态维度计数")
            StatusCounts status,
            @Schema(description = "厂商族维度计数")
            Map<String, EnabledTotal> types) {
    }

    /**
     * 状态维度计数。
     *
     * @param enabled  已启用数
     * @param disabled 已停用数
     * @param pending  待填密钥数
     * @param none     无密钥数
     */
    @Schema(name = "ProviderStatusCounts", description = "状态维度计数")
    public record StatusCounts(
            @Schema(description = "已启用数", required = true)
            long enabled,
            @Schema(description = "已停用数", required = true)
            long disabled,
            @Schema(description = "待填密钥数", required = true)
            long pending,
            @Schema(description = "无密钥数", required = true)
            long none) {
    }

    /**
     * 筛选 chip 组。
     *
     * @param status 状态快筛
     * @param types  厂商族筛选
     */
    @Schema(name = "ProviderListFilters", description = "筛选 chip 组")
    public record Filters(
            @Schema(description = "状态快筛")
            List<FilterChip> status,
            @Schema(description = "厂商族筛选")
            List<FilterChip> types) {
    }
}
