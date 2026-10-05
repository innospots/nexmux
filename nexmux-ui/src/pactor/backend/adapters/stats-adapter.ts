/**
 * 统计适配器：stats 端点原始聚合 → UI 契约形状（DashboardData / 用量行 / 可用性）。
 * 所有展示文案（fmt*）与 dashboard-mock / ops-usage-mock 逐字对齐。
 */

import type { DashboardData, TrendPoint } from "../contracts";
import type {
  ClientUsageRow,
  ModelProviderCompareRow,
  ModelUsageRow,
  ProviderQualityRow,
  ProviderUsageRow,
  RecentErrorRow,
  UsageDisplay,
  UsageSummary,
} from "../contracts";
import { backendClient, fetchAll } from "../client";
import { parseSpec } from "./quota-shared";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface DashboardVo {
  requests: number;
  tokens: number;
  costUsd: number;
  billedUsd: number;
  successRate: number;
  errorCount: number;
  avgLatencyMs: number;
  activeClients: number;
  trend: { t: string; requests: number; tokens: number; cost: number; avgLatencyMs: number }[];
  modelDistribution: { name: string; requests: number }[];
  clientDistribution: { name: string; requests: number }[];
  realtimeRpm: number;
  realtimeTokens: number;
}

interface UsageRowVo {
  key: string;
  requests: number;
  errorCount: number;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  standardUsd: number;
  billedUsd: number;
  costUsd: number;
  successRate: number;
  avgLatencyMs: number;
  avgTtftMs: number;
  avgUpstreamMs: number;
  failoverRate: number;
  avgAttempts: number;
}

interface AvailabilityVo {
  providers: QualityRowVo[];
  compare: QualityRowVo[];
  recentErrors: {
    requestId: string;
    requestedAt?: string;
    modelCode?: string;
    providerCode?: string;
    errorCode?: string;
  }[];
}

interface QualityRowVo {
  key: string;
  requests: number;
  errorCount: number;
  successRate: number;
  avgLatencyMs: number;
  avgUpstreamMs: number;
  failoverRate: number;
  avgAttempts: number;
  billedUsd: number;
  costUsd: number;
}

// ── 格式化（与 mock 逐字对齐） ────────────────────────────────

const fmtInt = (n: number) => Math.round(n).toLocaleString("en-US");

function fmtCompact(n: number): string {
  if (n >= 1e9) return `${(n / 1e9).toFixed(1)}B`;
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
  if (n >= 1e3) return `${(n / 1e3).toFixed(1)}K`;
  return String(Math.round(n));
}

const fmtMoney = (n: number) => `$${n.toFixed(2)}`;
const fmtMoney4 = (n: number) => `$${n.toFixed(4)}`;
const fmtPct = (x: number) => `${(x * 100).toFixed(1)}%`;
const fmtMs = (n: number) => `${Math.round(n)}ms`;

// ── 关联数据 ───────────────────────────────────────────────────

interface StatsRelations {
  modelNameByCode: Map<string, string>;
  providerNameByCode: Map<string, string>;
  clientById: Map<string, { code: string; name: string; appKeyId: string; tokenLimit?: number }>;
  routeNameByModel: Map<string, string>;
}

interface ClientLite {
  clientId: string;
  clientCode: string;
  displayName: string;
  appKeyId: string;
  quotaSpec?: string;
}

interface QuotaRuleLite {
  quotaType?: string;
  metric?: string;
  limit?: number;
}

async function loadRelations(): Promise<StatsRelations> {
  const [models, providers, clients, routes] = await Promise.all([
    fetchAll<{ modelCode: string; displayName: string }>("/api/nexmux/models"),
    fetchAll<{ providerCode: string; displayName: string }>("/api/nexmux/providers"),
    fetchAll<ClientLite>("/api/nexmux/clients"),
    fetchAll<{ displayName: string; routeSpec?: { candidates?: { modelCode: string }[] } }>(
      "/api/nexmux/routes",
    ),
  ]);
  const routeNameByModel = new Map<string, string>();
  for (const route of routes) {
    for (const candidate of route.routeSpec?.candidates ?? []) {
      if (!routeNameByModel.has(candidate.modelCode)) {
        routeNameByModel.set(candidate.modelCode, route.displayName);
      }
    }
  }
  return {
    modelNameByCode: new Map(models.map((m) => [m.modelCode, m.displayName])),
    providerNameByCode: new Map(providers.map((p) => [p.providerCode, p.displayName])),
    clientById: new Map(clients.map((c) => [
      c.clientId,
      {
        code: c.clientCode,
        name: c.displayName,
        appKeyId: c.appKeyId,
        tokenLimit: parseSpec<QuotaRuleLite>(c.quotaSpec).find(
          (q) => q.quotaType === "lifetime" && q.metric === "TOTAL_TOKEN",
        )?.limit,
      },
    ])),
    routeNameByModel,
  };
}

// ── Dashboard ──────────────────────────────────────────────────

function toDistribution(
  rows: { name: string; requests: number }[],
  nameOf: (name: string) => string,
): { name: string; percent: number }[] {
  const total = rows.reduce((sum, r) => sum + r.requests, 0);
  if (total === 0) return [];
  const items = rows.slice(0, 5).map((r) => ({
    name: nameOf(r.name),
    percent: Math.max(1, Math.round((r.requests / total) * 100)),
  }));
  const top = items.reduce((sum, it) => sum + it.percent, 0);
  items.push({ name: "其他", percent: Math.max(0, 100 - top) });
  return items;
}

export async function queryDashboardReal(
  start?: string,
  end?: string,
): Promise<DashboardData> {
  const [vo, relations] = await Promise.all([
    backendClient.get<DashboardVo>("/api/nexmux/stats/dashboard", { start, end }),
    loadRelations(),
  ]);
  const successPct = vo.successRate * 100;
  const trend: TrendPoint[] = vo.trend.map((t) => ({
    t: t.t.slice(5, 10),
    requests: t.requests,
    tokens: Number((t.tokens / 1_000_000).toFixed(2)),
    cost: t.cost,
    latency: Math.round(t.avgLatencyMs),
  }));
  return {
    realtime: { rpm: vo.realtimeRpm, tpm: fmtCompact(vo.realtimeTokens) },
    metricKeys: {
      requests: ["requests"],
      tokens: ["tokens"],
      cost: ["cost"],
      latency: ["latency"],
    },
    stats: {
      requests: fmtInt(vo.requests),
      activeClients: vo.activeClients,
      tokens: `${(vo.tokens / 1_000_000).toFixed(1)}M`,
      successRate: successPct.toFixed(1),
      failures: fmtInt(vo.errorCount),
      errorRate: (100 - successPct).toFixed(2),
      avgLatency: Math.round(vo.avgLatencyMs),
      cost: `$${vo.costUsd.toFixed(4)}`,
      avgCost: `$${(vo.requests === 0 ? 0 : vo.costUsd / vo.requests).toFixed(4)}`,
    },
    trend,
    models: toDistribution(vo.modelDistribution, (name) => relations.modelNameByCode.get(name) ?? name),
    clients: toDistribution(vo.clientDistribution, (name) => relations.clientById.get(name)?.name ?? name),
  };
}

// ── 用量行 ─────────────────────────────────────────────────────

function toUsageDisplay(row: UsageRowVo, rangeSeconds: number): UsageDisplay {
  const tokens = row.inputTokens + row.outputTokens;
  const cacheBase = row.inputTokens + row.cacheReadTokens;
  return {
    requests: fmtCompact(row.requests),
    inputCompact: fmtCompact(row.inputTokens),
    inputRaw: fmtInt(row.inputTokens),
    outputCompact: fmtCompact(row.outputTokens),
    outputRaw: fmtInt(row.outputTokens),
    cacheHit: fmtPct(cacheBase === 0 ? 0 : row.cacheReadTokens / cacheBase),
    standard: fmtMoney(row.standardUsd),
    billed: fmtMoney(row.billedUsd),
    cost: fmtMoney(row.costUsd),
    avgBilled: fmtMoney4(row.requests === 0 ? 0 : row.billedUsd / row.requests),
    successRate: fmtPct(row.successRate),
    avgLatency: fmtMs(row.avgLatencyMs),
    ttft: fmtMs(row.avgTtftMs),
    avgUpstream: fmtMs(row.avgUpstreamMs),
    tps: fmtCompact(rangeSeconds === 0 ? 0 : tokens / rangeSeconds),
    failoverRate: fmtPct(row.failoverRate),
    avgAttempts: row.avgAttempts.toFixed(2),
  };
}

function rangeSeconds(start?: string, end?: string): number {
  const s = start ? new Date(`${start}T00:00:00`).getTime() : 0;
  const e = end ? new Date(`${end}T23:59:59`).getTime() : Date.now();
  return Math.max(1, Math.round((e - s) / 1000));
}

function summarizeRows(rows: UsageRowVo[]): UsageSummary {
  const sum = (pick: (r: UsageRowVo) => number) => rows.reduce((acc, r) => acc + pick(r), 0);
  return {
    standard: fmtMoney(sum((r) => r.standardUsd)),
    billed: fmtMoney(sum((r) => r.billedUsd)),
    cost: fmtMoney(sum((r) => r.costUsd)),
  };
}

async function usageRows(start?: string, end?: string): Promise<UsageRowVo[]> {
  return backendClient.get<UsageRowVo[]>("/api/nexmux/stats/model-usage", { start, end });
}

export async function queryModelUsageReal(
  start?: string,
  end?: string,
): Promise<{ items: ModelUsageRow[]; summary: UsageSummary; total: number }> {
  const [rows, relations] = await Promise.all([usageRows(start, end), loadRelations()]);
  const seconds = rangeSeconds(start, end);
  return {
    items: rows.map((row) => ({
      key: row.key,
      model: relations.modelNameByCode.get(row.key) ?? row.key,
      route: relations.routeNameByModel.get(row.key) ?? "—",
      ...toUsageDisplay(row, seconds),
    })),
    summary: summarizeRows(rows),
    total: rows.length,
  };
}

export async function queryProviderUsageReal(
  start?: string,
  end?: string,
): Promise<{ items: ProviderUsageRow[]; summary: UsageSummary; total: number }> {
  const [rows, relations] = await Promise.all([
    backendClient.get<UsageRowVo[]>("/api/nexmux/stats/provider-usage", { start, end }),
    loadRelations(),
  ]);
  const seconds = rangeSeconds(start, end);
  return {
    items: rows.map((row) => ({
      key: row.key,
      provider: relations.providerNameByCode.get(row.key) ?? row.key,
      ...toUsageDisplay(row, seconds),
    })),
    summary: summarizeRows(rows),
    total: rows.length,
  };
}

export async function queryClientUsageReal(
  start?: string,
  end?: string,
): Promise<{ items: ClientUsageRow[]; summary: UsageSummary; total: number }> {
  const [rows, relations] = await Promise.all([
    backendClient.get<UsageRowVo[]>("/api/nexmux/stats/client-usage", { start, end }),
    loadRelations(),
  ]);
  const seconds = rangeSeconds(start, end);
  return {
    items: rows.map((row) => {
      const client = relations.clientById.get(row.key);
      const used = row.inputTokens + row.outputTokens;
      return {
        key: row.key,
        name: client?.name ?? row.key,
        code: client?.code ?? row.key,
        appKeyId: client?.appKeyId ?? "—",
        quotaUsage: client?.tokenLimit
          ? `${fmtCompact(used)}/${fmtCompact(client.tokenLimit)}`
          : "—",
        ...toUsageDisplay(row, seconds),
      };
    }),
    summary: summarizeRows(rows),
    total: rows.length,
  };
}

// ── 可用性 ─────────────────────────────────────────────────────

function toQualityDisplay(row: QualityRowVo) {
  return {
    requests: fmtCompact(row.requests),
    successRate: fmtPct(row.successRate),
    avgLatency: fmtMs(row.avgLatencyMs),
    avgUpstream: fmtMs(row.avgUpstreamMs),
    failoverRate: fmtPct(row.failoverRate),
    avgAttempts: row.avgAttempts.toFixed(2),
    standard: fmtMoney(row.costUsd),
    billed: fmtMoney(row.billedUsd),
    cost: fmtMoney(row.costUsd),
  };
}

export async function queryAvailabilityReal(
  start?: string,
  end?: string,
): Promise<{
  providers: ProviderQualityRow[];
  compare: ModelProviderCompareRow[];
  errors: RecentErrorRow[];
}> {
  const [vo, relations] = await Promise.all([
    backendClient.get<AvailabilityVo>("/api/nexmux/stats/provider-availability", { start, end }),
    loadRelations(),
  ]);
  const providers: ProviderQualityRow[] = vo.providers.map((row) => ({
    key: row.key,
    provider: relations.providerNameByCode.get(row.key) ?? row.key,
    errors: fmtCompact(row.errorCount),
    ...toQualityDisplay(row),
  }));
  const compare: ModelProviderCompareRow[] = vo.compare.map((row) => {
    const [modelCode, providerCode] = row.key.split("@");
    return {
      key: row.key,
      model: relations.modelNameByCode.get(modelCode) ?? modelCode,
      provider: relations.providerNameByCode.get(providerCode) ?? providerCode,
      ...toQualityDisplay(row),
    };
  });
  const errors: RecentErrorRow[] = vo.recentErrors.map((row) => ({
    id: row.requestId,
    time: row.requestedAt ? `${row.requestedAt.slice(5, 19).replace("T", " ")} UTC` : "—",
    target: `${relations.modelNameByCode.get(row.modelCode ?? "") ?? row.modelCode ?? "—"} / ${
      relations.providerNameByCode.get(row.providerCode ?? "") ?? row.providerCode ?? "—"
    }`,
    error: row.errorCode ?? "—",
  }));
  return { providers, compare, errors };
}
