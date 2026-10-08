package com.innospots.nexmux.console.ops;

import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Cost 展示记录（/api/d/costs UI 契约；列表行与详情共用）。
 * 定价明细（档位/倍率/计算过程文案）从 pricing_snapshot JSON 展开。
 *
 * @param id                  请求 ID
 * @param minutesAgo          距当前分钟数
 * @param time                完整时间
 * @param shortTime           短时间（MM-dd HH:mm）
 * @param application         应用
 * @param clientName          客户端名称（关联）
 * @param appKeyId            AppKey 展示 ID
 * @param alias               路由别名
 * @param modelCode           模型编码
 * @param modelName           模型名称（关联）
 * @param providerCode        提供方编码
 * @param providerName        提供方名称（关联）
 * @param currency            货币
 * @param inputTokens         输入 tokens
 * @param outputTokens        输出 tokens
 * @param cacheReadTokens     缓存读 tokens
 * @param priceVersion        定价版本
 * @param priceEffective      定价生效时间
 * @param tierUpto            档位上限（null 为开放档）
 * @param tierInputPrice      输入单价（/M）
 * @param tierOutputPrice     输出单价（/M）
 * @param tierCacheReadPrice  缓存读单价（/M）
 * @param multiplier          时段倍率
 * @param multiplierRule      倍率规则文案
 * @param tierLabel           档位标签（≤N / ∞）
 * @param multiplierText      倍率文案（Nx）
 * @param inputCost           输入成本（4 位小数字符串）
 * @param outputCost          输出成本
 * @param cacheCost           缓存成本（无缓存为 "—"）
 * @param totalCost           总成本
 * @param contextSummary      上下文摘要
 * @param tierDetail          档位明细文案
 * @param tierPriceText       档位价格文案
 * @param inputTokensText     输入 tokens 文案（千分位）
 * @param outputTokensText    输出 tokens 文案
 * @param cacheReadTokensText 缓存读 tokens 文案
 * @param inputCalcText       输入计算过程
 * @param outputCalcText      输出计算过程
 * @param cacheCalcText       缓存计算过程
 */
@Schema(name = "CostRecord", description = "Cost 展示记录（UI 契约）")
public record CostRecord(
        String id,
        long minutesAgo,
        String time,
        String shortTime,
        String application,
        String clientName,
        String appKeyId,
        String alias,
        String modelCode,
        String modelName,
        String providerCode,
        String providerName,
        String currency,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        String priceVersion,
        String priceEffective,
        Long tierUpto,
        BigDecimal tierInputPrice,
        BigDecimal tierOutputPrice,
        BigDecimal tierCacheReadPrice,
        BigDecimal multiplier,
        String multiplierRule,
        String tierLabel,
        String multiplierText,
        String inputCost,
        String outputCost,
        String cacheCost,
        String totalCost,
        String contextSummary,
        String tierDetail,
        String tierPriceText,
        String inputTokensText,
        String outputTokensText,
        String cacheReadTokensText,
        String inputCalcText,
        String outputCalcText,
        String cacheCalcText) {
}
