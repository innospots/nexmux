package com.innospots.nexmux.core.ops.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 网关请求日志概要（列表行）。
 */
@Schema(name = "RequestLogVo", description = "网关请求日志概要")
public record RequestLogVo(
        @Schema(description = "请求 ID", required = true)
        String requestId,
        @Schema(description = "客户端 ID")
        String clientId,
        @Schema(description = "路由 ID")
        String routeId,
        @Schema(description = "模型编码")
        String modelCode,
        @Schema(description = "状态")
        String status,
        @Schema(description = "耗时（毫秒）")
        Long latencyMs,
        @Schema(description = "错误码")
        String errorCode,
        @Schema(description = "路由轨迹")
        String routeTrace,
        @Schema(description = "请求时间", format = "date-time")
        LocalDateTime requestedAt,
        @Schema(description = "AppKey ID")
        String appKeyId,
        @Schema(description = "用户 Email")
        String userEmail,
        @Schema(description = "供应商编码")
        String providerCode,
        @Schema(description = "协议格式")
        String protocol,
        @Schema(description = "路由分组")
        String routeGroup,
        @Schema(description = "Application")
        String application,
        @Schema(description = "路由别名")
        String alias,
        @Schema(description = "HTTP 状态码")
        Integer httpStatus,
        @Schema(description = "输入 tokens")
        Long inputTokens,
        @Schema(description = "输出 tokens")
        Long outputTokens,
        @Schema(description = "缓存读 tokens")
        Long cacheReadTokens,
        @Schema(description = "缓存写 tokens")
        Long cacheWriteTokens,
        @Schema(description = "成本（USD）")
        BigDecimal costUsd,
        @Schema(description = "计费（USD）")
        BigDecimal billingUsd) {
}
