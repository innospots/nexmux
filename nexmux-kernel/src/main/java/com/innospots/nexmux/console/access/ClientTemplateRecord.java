package com.innospots.nexmux.console.access;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * ClientTemplate 展示记录（/api/d/client-templates UI 契约；列表行与详情共用，
 * 列表行中详情专有字段取缺省值）。
 *
 * @param code               模板编码（稳定业务键）
 * @param name               显示名称
 * @param tags               标签
 * @param description        描述
 * @param status             状态（enabled / disabled）
 * @param statusLabel        状态文案
 * @param validityType       有效期类型（days / fixed / never）
 * @param validityDays       创建后有效天数
 * @param validityFixedDate  固定到期日（yyyy-MM-dd）
 * @param validityLabel      有效期文案
 * @param authRoutes         授权路由快照
 * @param quotas             配额规则
 * @param rateLimits         限流规则
 * @param createdClientCount 基于本模板创建的客户端数
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param operator           最近操作人
 * @param routeCount         授权路由数
 * @param quotaSummary       配额摘要
 * @param rateLimitSummary   限流摘要
 * @param tagsText           标签文案
 * @param policySummary      策略摘要（N 路由 · 配额 · 限流）
 */
@Schema(name = "ClientTemplateRecord", description = "ClientTemplate 展示记录（UI 契约）")
public record ClientTemplateRecord(
        String code,
        String name,
        List<String> tags,
        String description,
        String status,
        String statusLabel,
        String validityType,
        Integer validityDays,
        String validityFixedDate,
        String validityLabel,
        List<AuthRoute> authRoutes,
        List<QuotaRule> quotas,
        List<RateLimitRule> rateLimits,
        long createdClientCount,
        String createdAt,
        String updatedAt,
        String operator,
        long routeCount,
        String quotaSummary,
        String rateLimitSummary,
        String tagsText,
        String policySummary) {
}
