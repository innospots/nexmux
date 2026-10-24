package com.innospots.nexmux.core.stats.domain.vo;

import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 维度用量聚合行（原始数值，展示格式化在 BFF）。
 *
 * @param key           维度值（model_code / provider_code / client_id）
 * @param requests      请求数
 * @param errorCount    失败请求数
 * @param inputTokens   输入 tokens
 * @param outputTokens  输出 tokens
 * @param cacheReadTokens 缓存读 tokens
 * @param standardUsd   标准参考金额合计
 * @param billedUsd     内部参考消费金额合计
 * @param costUsd       供应商成本估算合计
 * @param successRate   成功率（0-1）
 * @param avgLatencyMs  平均延迟
 * @param avgTtftMs     平均 TTFT
 * @param avgUpstreamMs 平均上游耗时
 * @param failoverRate  含重试请求占比（0-1）
 * @param avgAttempts   平均尝试次数（1 + 平均重试）
 */
@Schema(name = "UsageRowVo", description = "维度用量聚合行")
public record UsageRowVo(
        String key,
        long requests,
        long errorCount,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        BigDecimal standardUsd,
        BigDecimal billedUsd,
        BigDecimal costUsd,
        double successRate,
        double avgLatencyMs,
        double avgTtftMs,
        double avgUpstreamMs,
        double failoverRate,
        double avgAttempts) {
}
