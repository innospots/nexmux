package com.innospots.nexmux.console.stats;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商可用性视图（/api/d/stats-provider-availability UI 契约）。
 *
 * @param providers 供应商质量行
 * @param compare   模型 × 供应商对比行
 * @param errors    最近错误行
 */
@Schema(name = "AvailabilityView", description = "供应商可用性视图（UI 契约）")
public record AvailabilityView(
        List<ProviderQualityRow> providers,
        List<ModelProviderCompareRow> compare,
        List<RecentErrorRow> errors) {

    /**
     * 质量行展示字段（@JsonUnwrapped 平铺）。
     *
     * @param requests     请求数（缩略）
     * @param successRate  成功率
     * @param avgLatency   平均耗时
     * @param avgUpstream  平均上游耗时
     * @param failoverRate 故障转移率
     * @param avgAttempts  平均尝试次数
     * @param standard     标准价（同 cost，契约保留）
     * @param billed       计费
     * @param cost         成本
     */
    @Schema(name = "QualityDisplay", description = "质量行展示字段")
    public record QualityDisplay(
            String requests,
            String successRate,
            String avgLatency,
            String avgUpstream,
            String failoverRate,
            String avgAttempts,
            String standard,
            String billed,
            String cost) {
    }

    /**
     * 供应商质量行。
     *
     * @param key      供应商编码
     * @param provider 供应商名称
     * @param errors   错误数（缩略）
     * @param display  展示字段
     */
    @Schema(name = "ProviderQualityRow", description = "供应商质量行")
    public record ProviderQualityRow(
            String key,
            String provider,
            String errors,
            @JsonUnwrapped QualityDisplay display) {
    }

    /**
     * 模型 × 供应商对比行。
     *
     * @param key      组合键（model@provider）
     * @param model    模型名称
     * @param provider 供应商名称
     * @param display  展示字段
     */
    @Schema(name = "ModelProviderCompareRow", description = "模型 × 供应商对比行")
    public record ModelProviderCompareRow(
            String key,
            String model,
            String provider,
            @JsonUnwrapped QualityDisplay display) {
    }

    /**
     * 最近错误行。
     *
     * @param id     请求 ID
     * @param time   时间（UTC 标注）
     * @param target 目标（模型 / 供应商）
     * @param error  错误码
     */
    @Schema(name = "RecentErrorRow", description = "最近错误行")
    public record RecentErrorRow(
            String id,
            String time,
            String target,
            String error) {
    }
}
