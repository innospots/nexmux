package com.innospots.nexmux.core.ops.domain.vo;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 网关请求日志概要。
 *
 * @param requestId   请求 ID
 * @param clientId    客户端 ID
 * @param routeId     路由 ID
 * @param modelCode   模型编码
 * @param status      状态
 * @param latencyMs   耗时（毫秒）
 * @param errorCode   错误码
 * @param routeTrace  路由轨迹
 * @param requestedAt 请求时间
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
        LocalDateTime requestedAt) {
}
