package com.innospots.nexmux.console.access;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Client 展示记录（/api/d/clients UI 契约；列表行与详情共用，列表行中详情专有
 * 字段取缺省值）。expired 为展示层派生：ENABLED 且 expiresAt 已过 → expired。
 *
 * @param code                 客户端编码（稳定业务键）
 * @param name                 显示名称
 * @param tags                 标签
 * @param description          描述（后端无此字段，恒空串）
 * @param status               状态（enabled / disabled / expired）
 * @param statusLabel          状态文案
 * @param expiresAt            到期时间（yyyy-MM-dd HH:mm，空串 = 永不过期）
 * @param appKeyId             AppKey 展示 ID
 * @param secretLastRotatedAt  密钥最近轮换时间
 * @param templateCode         来源模板编码
 * @param templateName         来源模板名称
 * @param authRoutes           授权路由快照
 * @param quotas               配额规则
 * @param rateLimits           限流规则
 * @param createdAt            创建时间
 * @param updatedAt            更新时间
 * @param operator             最近操作人
 * @param routeCount           授权路由数
 * @param quotaSummary         配额摘要
 * @param rateLimitSummary     限流摘要
 * @param tagsText             标签文案
 * @param appKeySummary        AppKey 摘要（ID · 到期 · 路由数 · 限流）
 */
@Schema(name = "ClientRecord", description = "Client 展示记录（UI 契约）")
public record ClientRecord(
        @Schema(description = "客户端 ID", required = true)
        String id,
        String code,
        String name,
        List<String> tags,
        String description,
        String status,
        String statusLabel,
        String expiresAt,
        String appKeyId,
        String secretLastRotatedAt,
        String templateCode,
        String templateName,
        List<AuthRoute> authRoutes,
        List<QuotaRule> quotas,
        List<RateLimitRule> rateLimits,
        String createdAt,
        String updatedAt,
        String operator,
        long routeCount,
        String quotaSummary,
        String rateLimitSummary,
        String tagsText,
        String appKeySummary) {
}
