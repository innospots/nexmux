package com.innospots.nexmux.core.access.domain.request;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 更新客户端模板。
 *
 * @param displayName        显示名称
 * @param description        描述
 * @param tags               标签
 * @param validityType       有效期规则（days / fixed / never）
 * @param validityDays       有效天数
 * @param validityFixedDate  固定到期日（ISO 日期）
 * @param routeIds           授权路由 ID
 * @param quotaSpec          配额规格（JSON 原文）
 * @param rateLimitSpec      限流规格（JSON 原文）
 */
@Schema(name = "ClientTemplateUpdateRequest", description = "更新客户端模板")
public record ClientTemplateUpdateRequest(
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "描述")
        String description,
        @Schema(description = "标签")
        List<String> tags,
        @Schema(description = "有效期规则")
        String validityType,
        @Schema(description = "有效天数")
        Integer validityDays,
        @Schema(description = "固定到期日（ISO 日期）")
        String validityFixedDate,
        @Schema(description = "授权路由 ID")
        List<String> routeIds,
        @Schema(description = "配额规格（JSON 原文）")
        String quotaSpec,
        @Schema(description = "限流规格（JSON 原文）")
        String rateLimitSpec) {
}
