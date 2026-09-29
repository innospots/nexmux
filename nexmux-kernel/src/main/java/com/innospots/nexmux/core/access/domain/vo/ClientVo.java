package com.innospots.nexmux.core.access.domain.vo;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 接入客户端概要。
 *
 * @param clientId            客户端 ID
 * @param clientCode          客户端编码
 * @param displayName         显示名称
 * @param status              状态
 * @param authorizedRouteIds  已授权路由 ID
 * @param quotaSpec           配额规格
 * @param rateLimitSpec       限流规格
 * @param appKeyVersion       AppKey 版本号
 */
@Schema(name = "ClientVo", description = "接入客户端概要")
public record ClientVo(
        @Schema(description = "客户端 ID", required = true)
        String clientId,
        @Schema(description = "客户端编码", required = true)
        String clientCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ACTIVE"})
        String status,
        @Schema(description = "已授权路由 ID")
        List<String> authorizedRouteIds,
        @Schema(description = "配额规格")
        String quotaSpec,
        @Schema(description = "限流规格")
        String rateLimitSpec,
        @Schema(description = "AppKey 版本号")
        int appKeyVersion) {
}
