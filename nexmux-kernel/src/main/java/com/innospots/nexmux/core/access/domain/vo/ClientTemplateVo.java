package com.innospots.nexmux.core.access.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 客户端模板概要（列表行）。
 *
 * @param templateId         模板 ID
 * @param templateCode       模板编码
 * @param displayName        显示名称
 * @param status             状态
 * @param tags               标签（JSON 数组文本）
 * @param validityType       有效期规则（days / fixed / never）
 * @param validityDays       有效天数（days 时）
 * @param validTo            固定到期日（fixed 时）
 * @param authorizedRouteIds 授权路由 ID 列表
 * @param quotaSpec          配额规格（JSON 原文）
 * @param rateLimitSpec      限流规格（JSON 原文）
 * @param createdClientCount 基于本模板创建的客户端数
 */
@Schema(name = "ClientTemplateVo", description = "客户端模板概要")
public record ClientTemplateVo(
        @Schema(description = "模板 ID", required = true)
        String templateId,
        @Schema(description = "模板编码", required = true)
        String templateCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "标签（JSON 数组文本）")
        String tags,
        @Schema(description = "有效期规则", examples = {"days"})
        String validityType,
        @Schema(description = "有效天数")
        Integer validityDays,
        @Schema(description = "固定到期日", format = "date-time")
        LocalDateTime validTo,
        @Schema(description = "授权路由 ID 列表")
        List<String> authorizedRouteIds,
        @Schema(description = "配额规格（JSON 原文）")
        String quotaSpec,
        @Schema(description = "限流规格（JSON 原文）")
        String rateLimitSpec,
        @Schema(description = "基于本模板创建的客户端数")
        long createdClientCount) {
}
