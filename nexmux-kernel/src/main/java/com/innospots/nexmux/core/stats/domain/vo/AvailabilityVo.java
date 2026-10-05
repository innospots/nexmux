package com.innospots.nexmux.core.stats.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 供应商可用性聚合。
 *
 * @param providers    供应商质量行
 * @param compare      模型 × 供应商对照行
 * @param recentErrors 近期错误样本
 */
@Schema(name = "AvailabilityVo", description = "供应商可用性聚合")
public record AvailabilityVo(
        List<QualityRow> providers,
        List<QualityRow> compare,
        List<RecentError> recentErrors) {

    /**
     * 质量行。
     *
     * @param key           维度值（provider_code 或 modelCode@providerCode）
     * @param requests      请求数
     * @param errorCount    失败请求数
     * @param successRate   成功率
     * @param avgLatencyMs  平均延迟
     * @param avgUpstreamMs 平均上游耗时
     * @param failoverRate  含重试占比
     * @param avgAttempts   平均尝试次数
     * @param billedUsd     用户计费合计
     * @param costUsd       供应成本合计
     */
    public record QualityRow(
            String key,
            long requests,
            long errorCount,
            double successRate,
            double avgLatencyMs,
            double avgUpstreamMs,
            double failoverRate,
            double avgAttempts,
            BigDecimal billedUsd,
            BigDecimal costUsd) {
    }

    /**
     * 近期错误样本。
     *
     * @param requestId   请求 ID
     * @param requestedAt 请求时间（ISO 文本）
     * @param modelCode   模型编码
     * @param providerCode 供应商编码
     * @param errorCode   错误码
     */
    public record RecentError(
            String requestId,
            String requestedAt,
            String modelCode,
            String providerCode,
            String errorCode) {
    }
}
