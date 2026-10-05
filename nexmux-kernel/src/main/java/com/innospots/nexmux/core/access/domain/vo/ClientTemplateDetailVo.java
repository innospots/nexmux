package com.innospots.nexmux.core.access.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 客户端模板详情（按稳定业务键查询）。
 *
 * @param templateId         模板 ID
 * @param templateCode       模板编码（稳定业务键）
 * @param displayName        显示名称
 * @param status             状态
 * @param description        描述
 * @param tags               标签列表
 * @param validityType       有效期规则（days / fixed / never）
 * @param validityDays       有效天数
 * @param validTo            固定到期日
 * @param authorizedRouteIds 授权路由 ID 列表
 * @param quotaSpec          配额规格（JSON 原文）
 * @param rateLimitSpec      限流规格（JSON 原文）
 * @param createdClientCount 基于本模板创建的客户端数
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param updatedBy          最近操作人
 */
@Schema(name = "ClientTemplateDetailVo", description = "客户端模板详情")
public record ClientTemplateDetailVo(
        @Schema(description = "模板 ID", required = true)
        String templateId,
        @Schema(description = "模板编码", required = true)
        String templateCode,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "状态", examples = {"ENABLED"})
        String status,
        @Schema(description = "描述")
        String description,
        @Schema(description = "标签列表")
        List<String> tags,
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
        long createdClientCount,
        @Schema(description = "创建时间", format = "date-time")
        LocalDateTime createdAt,
        @Schema(description = "更新时间", format = "date-time")
        LocalDateTime updatedAt,
        @Schema(description = "最近操作人")
        String updatedBy) {
}
