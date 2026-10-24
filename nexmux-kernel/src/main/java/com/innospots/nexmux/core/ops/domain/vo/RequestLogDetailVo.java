package com.innospots.nexmux.core.ops.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 网关请求日志详情（含追踪、上游与快照原文）。
 */
@Schema(name = "RequestLogDetailVo", description = "网关请求日志详情")
public record RequestLogDetailVo(
        String requestId,
        String clientId,
        String routeId,
        String modelCode,
        String status,
        Long latencyMs,
        String errorCode,
        String routeTrace,
        LocalDateTime requestedAt,
        String appKeyId,
        String userEmail,
        String providerCode,
        String protocol,
        String routeGroup,
        String application,
        String alias,
        Integer httpStatus,
        Long inputTokens,
        Long outputTokens,
        Long cacheReadTokens,
        Long cacheWriteTokens,
        BigDecimal costUsd,
        BigDecimal billingUsd,
        @Schema(description = "首 token 耗时（毫秒）")
        Long ttftMs,
        @Schema(description = "上游模型 ID")
        String upstreamModel,
        @Schema(description = "上游端点")
        String upstreamEndpoint,
        @Schema(description = "上游状态")
        String upstreamStatus,
        @Schema(description = "上游耗时（毫秒）")
        Long upstreamLatencyMs,
        @Schema(description = "重试次数")
        Integer retries,
        @Schema(description = "客户端 IP")
        String clientIp,
        @Schema(description = "入口方法")
        String ingressMethod,
        @Schema(description = "入口路径")
        String ingressPath,
        @Schema(description = "追踪 ID")
        String traceId,
        @Schema(description = "错误类型")
        String errorType,
        @Schema(description = "定价快照（JSON 原文）")
        String pricingSnapshot,
        @Schema(description = "Prompt/Response 快照（JSON 原文，受保留策略截断）")
        String promptSnapshot,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt) {
}
