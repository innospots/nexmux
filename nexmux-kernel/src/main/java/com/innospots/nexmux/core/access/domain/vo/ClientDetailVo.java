package com.innospots.nexmux.core.access.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 接入客户端详情（按稳定业务键查询；不含 AppKey 明文与完整哈希）。
 *
 * @param clientId           客户端 ID
 * @param clientCode         客户端编码（稳定业务键）
 * @param displayName        显示名称
 * @param status             状态（ENABLED / DISABLED）
 * @param appKeyId           AppKey 展示 ID
 * @param appKeyVersion      AppKey 版本号
 * @param appKeyRotatedAt    密钥最近轮换时间
 * @param authorizedRouteIds 已授权路由 ID 列表
 * @param quotaSpec          配额规格（JSON 原文）
 * @param rateLimitSpec      限流规格（JSON 原文）
 * @param sourceTemplateId   来源模板 ID
 * @param tags               标签列表
 * @param expiresAt          到期时间
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param updatedBy          最近操作人
 */
@Schema(name = "ClientDetailVo", description = "接入客户端详情")
public record ClientDetailVo(
        @Schema(description = "客户端 ID", required = true)
        String clientId,
        @Schema(description = "客户端编码", required = true)
        String clientCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "AppKey 展示 ID")
        String appKeyId,
        @Schema(description = "AppKey 版本号")
        int appKeyVersion,
        @Schema(description = "密钥最近轮换时间", format = "date-time")
        LocalDateTime appKeyRotatedAt,
        @Schema(description = "已授权路由 ID 列表")
        List<String> authorizedRouteIds,
        @Schema(description = "配额规格（JSON 原文）")
        String quotaSpec,
        @Schema(description = "限流规格（JSON 原文）")
        String rateLimitSpec,
        @Schema(description = "来源模板 ID")
        String sourceTemplateId,
        @Schema(description = "标签列表")
        List<String> tags,
        @Schema(description = "到期时间", format = "date-time")
        LocalDateTime expiresAt,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt,
        @Schema(description = "更新时间", format = "date-time")
        LocalDateTime updatedAt,
        @Schema(description = "最近操作人")
        String updatedBy) {
}
