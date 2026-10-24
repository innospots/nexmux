package com.innospots.nexmux.core.ops.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 成本明细记录（成功且已计费请求日志的计费视图；requestId 与调用日志一致）。
 */
@Schema(name = "CostRecordVo", description = "成本明细记录")
public record CostRecordVo(
        @Schema(description = "请求 ID", required = true)
        String requestId,
        @Schema(description = "请求时间", format = "date-time")
        LocalDateTime requestedAt,
        @Schema(description = "Application")
        String application,
        @Schema(description = "客户端 ID")
        String clientId,
        @Schema(description = "AppKey ID")
        String appKeyId,
        @Schema(description = "路由别名")
        String alias,
        @Schema(description = "模型编码")
        String modelCode,
        @Schema(description = "供应商编码")
        String providerCode,
        @Schema(description = "输入 tokens")
        Long inputTokens,
        @Schema(description = "输出 tokens")
        Long outputTokens,
        @Schema(description = "缓存读 tokens")
        Long cacheReadTokens,
        @Schema(description = "成本（USD）")
        BigDecimal costUsd,
        @Schema(description = "计费（USD）")
        BigDecimal billingUsd,
        @Schema(description = "定价快照（JSON 原文：档位/倍率/价格版本）")
        String pricingSnapshot) {
}
