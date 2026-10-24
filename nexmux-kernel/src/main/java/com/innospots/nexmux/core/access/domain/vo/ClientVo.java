package com.innospots.nexmux.core.access.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 接入客户端概要（列表行）。
 *
 * @param clientId           客户端 ID
 * @param clientCode         客户端编码
 * @param displayName        显示名称
 * @param status             状态
 * @param authorizedRouteIds 已授权路由 ID
 * @param quotaSpec          配额规格
 * @param rateLimitSpec      限流规格
 * @param appKeyVersion      AppKey 版本号
 * @param appKeyId           AppKey 展示 ID（gw-ak- 前缀，由哈希派生，不含敏感信息）
 * @param tags               标签
 * @param sourceTemplateId   来源模板 ID
 * @param expiresAt          到期时间
 * @param appKeyRotatedAt    密钥最近轮换时间
 */
@Schema(name = "ClientVo", description = "接入客户端概要")
public record ClientVo(
        @Schema(description = "客户端 ID", required = true)
        String clientId,
        @Schema(description = "客户端编码", required = true)
        String clientCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "已授权路由 ID")
        List<String> authorizedRouteIds,
        @Schema(description = "配额规格")
        String quotaSpec,
        @Schema(description = "限流规格")
        String rateLimitSpec,
        @Schema(description = "AppKey 版本号")
        int appKeyVersion,
        @Schema(description = "AppKey 展示 ID")
        String appKeyId,
        @Schema(description = "标签（JSON 数组文本）")
        String tags,
        @Schema(description = "来源模板 ID")
        String sourceTemplateId,
        @Schema(description = "到期时间", format = "date-time")
        LocalDateTime expiresAt,
        @Schema(description = "密钥最近轮换时间", format = "date-time")
        LocalDateTime appKeyRotatedAt) {
}
