/**
 * 配额 / 限流规则的展示派生（与 client-templates-mock / clients-mock 逐字对齐）。
 * Client 与 ClientTemplate 两个 adapter 共用；JSON 原文存于后端 *_spec CLOB。
 */

import type {
  CycleUnit,
  QuotaRule,
  RateLimitRule,
} from "../../mocks/client-templates-mock";

export const QUOTA_TYPE_LABELS: Record<string, string> = {
  lifetime: "总配额",
  cyclic: "周期循环配额",
};

export const QUOTA_METRIC_LABELS: Record<string, string> = {
  REQUEST: "Request",
  INPUT_TOKEN: "Input Token",
  OUTPUT_TOKEN: "Output Token",
  TOTAL_TOKEN: "Total Token",
  COST: "Cost",
  CONCURRENT: "Concurrent",
};

export const QUOTA_MODE_LABELS: Record<string, string> = {
  soft: "Soft Limit",
  hard: "Hard Limit",
};

export const RATE_LIMIT_TYPE_LABELS: Record<string, string> = {
  RPM: "RPM（每分钟请求数）",
  TPM: "TPM（每分钟 Token 数）",
  CONCURRENT: "Concurrent（并发连接数）",
};

export function thousands(n: number): string {
  return n.toLocaleString("en-US");
}

export function cycleLabel(unit: CycleUnit, value?: number): string {
  if (unit === "month") return "每月（自然月）";
  if (unit === "day") return `每 ${value ?? 1} 天`;
  return `每 ${value ?? 24} 小时`;
}

/** 配额规则 JSON → 含展示标签的完整规则 */
export function deriveQuota(rule: QuotaRule): QuotaRule {
  return {
    ...rule,
    quotaTypeLabel: QUOTA_TYPE_LABELS[rule.quotaType] ?? rule.quotaType,
    metricLabel: QUOTA_METRIC_LABELS[rule.metric] ?? rule.metric,
    limitText: thousands(rule.limit),
    modeLabel: QUOTA_MODE_LABELS[rule.mode] ?? rule.mode,
    cycleLabel:
      rule.quotaType === "cyclic" && rule.cycleUnit
        ? cycleLabel(rule.cycleUnit, rule.cycleValue)
        : undefined,
  };
}

/** 限流规则 JSON → 含展示标签的完整规则（scopeRouteNames 由调用方按路由 join 补） */
export function deriveRateLimit(
  rule: RateLimitRule,
  scopeRouteNames?: string[],
): RateLimitRule {
  return {
    ...rule,
    limitTypeLabel: RATE_LIMIT_TYPE_LABELS[rule.limitType] ?? rule.limitType,
    limitText: thousands(rule.limit),
    scopeLabel: rule.scopeAll ? "全部授权路由" : (scopeRouteNames ?? []).join("、") || "—",
  };
}

/** 配额摘要：总+日循环 / 总+周循环 / 月循环 …；无规则为 — */
export function quotaSummaryOf(quotas: QuotaRule[]): string {
  const parts = quotas.map((q) => {
    if (q.quotaType === "lifetime") return "总";
    if (q.cycleUnit === "month") return "月循环";
    if (q.cycleUnit === "day") return q.cycleValue === 7 ? "周循环" : "日循环";
    return "日循环";
  });
  return parts.length > 0 ? parts.join("+") : "—";
}

/** 限流摘要：首个启用规则（否则首条）的紧凑文案；无规则为 — */
export function rateLimitSummaryOf(rateLimits: RateLimitRule[]): string {
  const r = rateLimits.find((x) => x.status === "enabled") ?? rateLimits[0];
  if (!r) return "—";
  if (r.limitType === "CONCURRENT") return `${r.limit} 并发`;
  return `${r.limit} ${r.limitType}`;
}

/** 解析 *_spec CLOB JSON（容错：空/非法 → 空数组） */
export function parseSpec<T>(text: string | null | undefined): T[] {
  if (!text) return [];
  try {
    const parsed: unknown = JSON.parse(text);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}
