package com.innospots.nexmux.console.access;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 配额规则（Client / ClientTemplate 共用；JSON 原文存于 quota_spec CLOB，
 * 展示标签由 {@link PolicyDerive} 预计算；Client 侧运行用量字段 Runtime
 * 未建，统一 null）。
 *
 * @param name           规则名称
 * @param quotaType      配额类型（lifetime / cyclic）
 * @param quotaTypeLabel 类型文案（总配额 / 周期循环配额）
 * @param metric         计量指标
 * @param metricLabel    指标文案
 * @param limit          上限
 * @param limitText      千分位上限文案
 * @param mode           模式（soft / hard）
 * @param modeLabel      模式文案
 * @param cycleUnit      循环周期单位（hour / day / month，仅 cyclic）
 * @param cycleValue     循环周期值
 * @param cycleLabel     周期文案（如 每 24 小时 / 每月（自然月））
 * @param status         状态（enabled / disabled）
 * @param usageUsed      已用量（Runtime 未建，恒 null）
 * @param usageUsedText  千分位已用量文案
 * @param usagePercent   用量百分比
 * @param nextResetAt    下次重置时间
 */
@Schema(name = "QuotaRule", description = "配额规则")
public record QuotaRule(
        String name,
        String quotaType,
        String quotaTypeLabel,
        String metric,
        String metricLabel,
        long limit,
        String limitText,
        String mode,
        String modeLabel,
        String cycleUnit,
        Integer cycleValue,
        String cycleLabel,
        String status,
        Long usageUsed,
        String usageUsedText,
        Integer usagePercent,
        String nextResetAt) {
}
