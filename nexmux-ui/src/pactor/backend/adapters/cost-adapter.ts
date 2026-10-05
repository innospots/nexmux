/**
 * 成本明细适配器：CostRecordVo → UI 契约形状（CostRecord）。
 * 定价明细（档位/倍率/计算过程文案）从 pricing_snapshot JSON 展开。
 */

import type { CostListResult, CostRecord } from "../contracts";
import { backendClient, fetchAll, type PageResult } from "../client";
import {
  compactTokens,
  fullTime,
  minuteTime,
  parseJsonSafe,
  thousands,
} from "./format-shared";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface CostRecordVo {
  requestId: string;
  requestedAt?: string;
  application?: string;
  clientId?: string;
  appKeyId?: string;
  alias?: string;
  modelCode?: string;
  providerCode?: string;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  costUsd?: number;
  billingUsd?: number;
  pricingSnapshot?: string;
}

interface PricingSnapshot {
  currency?: string;
  priceVersion?: string;
  priceEffective?: string;
  tierUpto?: number | null;
  tierInputPrice?: number;
  tierOutputPrice?: number;
  tierCacheReadPrice?: number | null;
  multiplier?: number;
  multiplierRule?: string;
  costRate?: number;
  billingRate?: number;
}

// ── 关联数据 ───────────────────────────────────────────────────

interface CostRelations {
  clientNameById: Map<string, string>;
  modelNameByCode: Map<string, string>;
  providerNameByCode: Map<string, string>;
}

async function loadRelations(): Promise<CostRelations> {
  const [clients, models, providers] = await Promise.all([
    fetchAll<{ clientId: string; displayName: string }>("/api/nexmux/clients"),
    fetchAll<{ modelCode: string; displayName: string }>("/api/nexmux/models"),
    fetchAll<{ providerCode: string; displayName: string }>("/api/nexmux/providers"),
  ]);
  return {
    clientNameById: new Map(clients.map((c) => [c.clientId, c.displayName])),
    modelNameByCode: new Map(models.map((m) => [m.modelCode, m.displayName])),
    providerNameByCode: new Map(providers.map((p) => [p.providerCode, p.displayName])),
  };
}

// ── 派生 ───────────────────────────────────────────────────────

function tierLabelOf(upto: number | null | undefined): string {
  return upto == null ? "∞" : `≤${compactTokens(upto)}`;
}

function tierDetailOf(upto: number | null | undefined): string {
  return upto == null ? "UPTO ∞（无上限档）" : `UPTO ≤ ${thousands(upto)}`;
}

// ── VO → CostRecord ───────────────────────────────────────────

function toRecord(vo: CostRecordVo, relations: CostRelations): CostRecord {
  const snap = parseJsonSafe<PricingSnapshot>(vo.pricingSnapshot, {});
  const input = vo.inputTokens ?? 0;
  const output = vo.outputTokens ?? 0;
  const cacheRead = vo.cacheReadTokens ?? 0;
  const multiplier = snap.multiplier ?? 1;
  const tierInput = snap.tierInputPrice ?? 0;
  const tierOutput = snap.tierOutputPrice ?? 0;
  const tierCache = snap.tierCacheReadPrice ?? null;

  const inputCost = (input * tierInput * multiplier) / 1_000_000;
  const outputCost = (output * tierOutput * multiplier) / 1_000_000;
  const cacheCost = cacheRead > 0 && tierCache != null ? (cacheRead * tierCache * multiplier) / 1_000_000 : null;
  const totalCost = vo.costUsd ?? inputCost + outputCost + (cacheCost ?? 0);

  const clientName = relations.clientNameById.get(vo.clientId ?? "") ?? "—";
  const modelName = relations.modelNameByCode.get(vo.modelCode ?? "") ?? vo.modelCode ?? "—";
  const providerName = relations.providerNameByCode.get(vo.providerCode ?? "") ?? vo.providerCode ?? "—";
  const time = fullTime(vo.requestedAt);
  const short = minuteTime(vo.requestedAt);
  const minutesAgo = vo.requestedAt
    ? Math.max(0, Math.round((Date.now() - new Date(vo.requestedAt).getTime()) / 60000))
    : 0;

  return {
    id: vo.requestId,
    minutesAgo,
    time,
    shortTime: short,
    application: vo.application ?? "—",
    clientName,
    appKeyId: vo.appKeyId ?? "—",
    alias: vo.alias ?? "—",
    modelCode: vo.modelCode ?? "—",
    modelName,
    providerCode: vo.providerCode ?? "—",
    providerName,
    currency: snap.currency ?? "USD",
    inputTokens: input,
    outputTokens: output,
    cacheReadTokens: cacheRead,
    priceVersion: snap.priceVersion ?? "—",
    priceEffective: snap.priceEffective ?? "—",
    tierUpto: snap.tierUpto ?? null,
    tierInputPrice: tierInput,
    tierOutputPrice: tierOutput,
    tierCacheReadPrice: tierCache,
    multiplier,
    multiplierRule: snap.multiplierRule ?? (multiplier === 1 ? "未命中时段倍率（按 1.0 计）" : `时段倍率 ×${multiplier}`),
    tierLabel: tierLabelOf(snap.tierUpto),
    multiplierText: `${multiplier}x`,
    inputCost: inputCost.toFixed(4),
    outputCost: outputCost.toFixed(4),
    cacheCost: cacheCost != null ? cacheCost.toFixed(4) : "—",
    totalCost: totalCost.toFixed(4),
    contextSummary: `${short} · ${clientName}（${vo.appKeyId ?? "—"}）· ${modelName} · ${providerName}`,
    tierDetail: tierDetailOf(snap.tierUpto),
    tierPriceText: `输入 $${tierInput}/M · 输出 $${tierOutput}/M · 缓存读 ${tierCache != null ? `$${tierCache}/M` : "—"}`,
    inputTokensText: thousands(input),
    outputTokensText: thousands(output),
    cacheReadTokensText: cacheRead > 0 ? thousands(cacheRead) : "—",
    inputCalcText: `${thousands(input)} × $${tierInput} × ${multiplier} ÷ 1M = $${inputCost.toFixed(4)}`,
    outputCalcText: `${thousands(output)} × $${tierOutput} × ${multiplier} ÷ 1M = $${outputCost.toFixed(4)}`,
    cacheCalcText:
      cacheCost != null && tierCache != null
        ? `${thousands(cacheRead)} × $${tierCache} × ${multiplier} ÷ 1M = $${cacheCost.toFixed(4)}`
        : "无缓存用量",
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function listCosts(params: {
  application?: string;
  appKey?: string;
  alias?: string;
  model?: string;
  provider?: string;
  start?: string;
  end?: string;
}): Promise<CostListResult> {
  const [page, relations] = await Promise.all([
    backendClient.get<PageResult<CostRecordVo>>("/api/nexmux/costs", {
      pageNo: 1,
      pageSize: 5000,
      application: params.application,
      appKey: params.appKey,
      alias: params.alias,
      model: params.model && params.model !== "all" ? params.model : undefined,
      provider: params.provider && params.provider !== "all" ? params.provider : undefined,
      start: params.start,
      end: params.end,
    }),
    loadRelations(),
  ]);

  return {
    items: page.records.map((vo) => toRecord(vo, relations)),
    total: page.total,
  };
}

export async function getCostById(id: string): Promise<CostRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<CostRecordVo>(`/api/nexmux/costs/${encodeURIComponent(id)}`),
    loadRelations(),
  ]);
  return toRecord(detail, relations);
}
