package com.innospots.nexmux.core.stats.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 仪表盘聚合（原始数值，展示格式化在 BFF）。
 *
 * @param requests          总请求数
 * @param tokens            总 tokens（输入+输出）
 * @param costUsd           供应成本合计
 * @param billedUsd         用户计费合计
 * @param successRate       成功率（0-1）
 * @param errorCount        失败请求数
 * @param avgLatencyMs      平均延迟
 * @param activeClients     活跃客户端数（distinct client_id）
 * @param trend             按天趋势
 * @param modelDistribution 模型请求分布
 * @param clientDistribution 客户端请求分布
 * @param realtimeRpm       最近 60 秒请求数
 * @param realtimeTokens    最近 60 秒 tokens
 */
@Schema(name = "DashboardVo", description = "仪表盘聚合")
public record DashboardVo(
        long requests,
        long tokens,
        BigDecimal costUsd,
        BigDecimal billedUsd,
        double successRate,
        long errorCount,
        double avgLatencyMs,
        long activeClients,
        List<TrendPoint> trend,
        List<DimensionCount> modelDistribution,
        List<DimensionCount> clientDistribution,
        long realtimeRpm,
        long realtimeTokens) {

    /**
     * 趋势点。
     *
     * @param t           日期（yyyy-MM-dd）
     * @param requests    请求数
     * @param tokens      tokens（百万为单位前的原始值）
     * @param cost        计费
     * @param avgLatencyMs 平均延迟
     */
    public record TrendPoint(String t, long requests, long tokens, BigDecimal cost, double avgLatencyMs) {
    }

    /**
     * 维度计数。
     *
     * @param name     维度值（model_code / client_id）
     * @param requests 请求数
     */
    public record DimensionCount(String name, long requests) {
    }
}
