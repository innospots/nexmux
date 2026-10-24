package com.innospots.nexmux.console.access;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 限流规则（Client / ClientTemplate 共用；JSON 原文存于 rate_limit_spec CLOB，
 * 展示标签由 {@link PolicyDerive} 预计算）。
 *
 * @param name          规则名称
 * @param limitType     限流类型（RPM / TPM / CONCURRENT）
 * @param limitTypeLabel 类型文案
 * @param limit         上限
 * @param limitText     千分位上限文案
 * @param burst         突发容量（可空）
 * @param scopeAll      是否全部授权路由
 * @param scopeRoutes   指定路由 ID 列表（scopeAll=false 时）
 * @param scopeLabel    范围文案（全部授权路由 / 路由名列表）
 * @param status        状态（enabled / disabled）
 */
@Schema(name = "RateLimitRule", description = "限流规则")
public record RateLimitRule(
        String name,
        String limitType,
        String limitTypeLabel,
        long limit,
        String limitText,
        Long burst,
        boolean scopeAll,
        List<String> scopeRoutes,
        String scopeLabel,
        String status) {
}
