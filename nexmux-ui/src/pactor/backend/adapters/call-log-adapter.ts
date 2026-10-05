/**
 * 调用日志适配器：RequestLogVo/DetailVo → UI 契约形状（CallLogRecord）。
 *
 * join 关系：clientId→clients、routeId→routes、modelCode→models、providerCode→providers。
 * mock 中的 channel/routeTag 等演示元数据无真实列，诚实给 "—"；authResult 由 httpStatus 规则化。
 */

import type {
  CallLogListResult,
  CallLogRecord,
  TraceStep,
} from "../contracts";
import { backendClient, fetchAll, type PageResult } from "../client";
import {
  compactTokens,
  fullTime,
  parseJsonSafe,
  shortTime,
  thousands,
} from "./format-shared";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface RequestLogVo {
  requestId: string;
  clientId?: string;
  routeId?: string;
  modelCode?: string;
  status: string;
  latencyMs?: number;
  errorCode?: string;
  routeTrace?: string;
  requestedAt?: string;
  appKeyId?: string;
  userEmail?: string;
  providerCode?: string;
  protocol?: string;
  routeGroup?: string;
  application?: string;
  alias?: string;
  httpStatus?: number;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  cacheWriteTokens?: number;
  costUsd?: number;
  billingUsd?: number;
}

interface RequestLogDetailVo extends RequestLogVo {
  ttftMs?: number;
  upstreamModel?: string;
  upstreamEndpoint?: string;
  upstreamStatus?: string;
  upstreamLatencyMs?: number;
  retries?: number;
  clientIp?: string;
  ingressMethod?: string;
  ingressPath?: string;
  traceId?: string;
  errorType?: string;
  pricingSnapshot?: string;
  promptSnapshot?: string;
}

interface PricingSnapshot {
  priceVersion?: string;
  priceEffective?: string;
  costRate?: number;
  billingRate?: number;
  multiplier?: number;
  multiplierRule?: string;
}

interface PromptSnapshot {
  policy?: string;
  policyNote?: string;
  prompt?: string | null;
  response?: string | null;
}

// ── 常量（与 call-logs-mock 逐字对齐） ─────────────────────────

const STATUS_LABELS: Record<string, string> = {
  success: "成功",
  error: "错误",
  unfinished: "未完成",
  cancelled: "已取消",
  other: "其他",
};

// ── 关联数据 ───────────────────────────────────────────────────

interface LogRelations {
  clientNameById: Map<string, string>;
  routeById: Map<string, { code: string; name: string; strategyLabel: string }>;
  modelNameByCode: Map<string, string>;
  providerNameByCode: Map<string, string>;
}

const STRATEGY_LABELS: Record<string, string> = {
  WEIGHTED: "加权轮询",
  HASH: "哈希分配",
  PRIORITY: "优先级",
  ROUND_ROBIN: "轮询",
  RANDOM: "随机",
  FIXED: "固定",
};

async function loadRelations(): Promise<LogRelations> {
  const [clients, routes, models, providers] = await Promise.all([
    fetchAll<{ clientId: string; displayName: string }>("/api/nexmux/clients"),
    fetchAll<{ routeId: string; routeCode: string; displayName: string; routeSpec?: { strategyType?: string } }>(
      "/api/nexmux/routes",
    ),
    fetchAll<{ modelCode: string; displayName: string }>("/api/nexmux/models"),
    fetchAll<{ providerCode: string; displayName: string }>("/api/nexmux/providers"),
  ]);
  return {
    clientNameById: new Map(clients.map((c) => [c.clientId, c.displayName])),
    routeById: new Map(routes.map((r) => [
      r.routeId,
      {
        code: r.routeCode,
        name: r.displayName,
        strategyLabel: STRATEGY_LABELS[r.routeSpec?.strategyType ?? ""] ?? "—",
      },
    ])),
    modelNameByCode: new Map(models.map((m) => [m.modelCode, m.displayName])),
    providerNameByCode: new Map(providers.map((p) => [p.providerCode, p.displayName])),
  };
}

// ── VO → CallLogRecord ────────────────────────────────────────

function statusOf(backendStatus: string): CallLogRecord["status"] {
  const key = backendStatus?.toLowerCase();
  return (["success", "error", "unfinished", "cancelled", "other"].includes(key)
    ? key
    : "other") as CallLogRecord["status"];
}

function pricingText(snapshot?: string): string {
  const s = parseJsonSafe<PricingSnapshot | null>(snapshot, null);
  if (!s?.priceVersion) return "—";
  const parts = [`${s.priceVersion}（${s.priceEffective ?? "—"} 生效）`];
  if (s.costRate != null) parts.push(`C=${s.costRate}`);
  if (s.billingRate != null) parts.push(`M=${s.billingRate}`);
  parts.push(s.multiplier && s.multiplier !== 1 ? `时段倍率 ×${s.multiplier}` : "无时段倍率");
  return parts.join(" · ");
}

function toRecord(
  vo: RequestLogVo,
  relations: LogRelations,
  detail?: RequestLogDetailVo,
): CallLogRecord {
  const status = statusOf(vo.status);
  const input = vo.inputTokens ?? null;
  const output = vo.outputTokens ?? null;
  const total = (input ?? 0) + (output ?? 0);
  const cost = vo.costUsd ?? null;
  const billing = vo.billingUsd ?? null;
  const margin = billing != null && cost != null ? billing - cost : null;
  const route = relations.routeById.get(vo.routeId ?? "");
  const time = fullTime(vo.requestedAt);
  const prompt = parseJsonSafe<PromptSnapshot | null>(detail?.promptSnapshot, null);

  return {
    id: vo.requestId,
    status,
    statusLabel: STATUS_LABELS[status],
    timeUtc: `${time} UTC`,
    timeShort: `${shortTime(vo.requestedAt)} UTC`,
    latencyMs: vo.latencyMs ?? 0,
    latencyText: `${vo.latencyMs ?? 0}ms`,
    userEmail: vo.userEmail ?? "—",
    clientCode: relations.clientNameById.get(vo.clientId ?? "") ?? "—",
    clientName: relations.clientNameById.get(vo.clientId ?? "") ?? "—",
    appKeyId: vo.appKeyId ?? "—",
    channel: "—",
    routeTag: "—",
    application: vo.application ?? "—",
    tagsText: vo.application ?? "—",
    routeCode: route?.code ?? "—",
    routeName: route?.name ?? "—",
    routeGroup: vo.routeGroup ?? "—",
    protocol: vo.protocol ?? "—",
    modelCode: vo.modelCode ?? "—",
    modelName: relations.modelNameByCode.get(vo.modelCode ?? "") ?? vo.modelCode ?? "—",
    providerCode: vo.providerCode ?? "—",
    providerName: relations.providerNameByCode.get(vo.providerCode ?? "") ?? vo.providerCode ?? "—",
    inputTokens: input,
    outputTokens: output,
    cacheReadTokens: vo.cacheReadTokens ?? 0,
    cacheWriteTokens: vo.cacheWriteTokens ?? 0,
    usageText: input != null ? `${compactTokens(input)}/${output != null ? compactTokens(output) : "—"}` : "—",
    inputTokensText: input != null ? thousands(input) : "—",
    outputTokensText: output != null ? thousands(output) : "—",
    cacheReadText: thousands(vo.cacheReadTokens ?? 0),
    cacheWriteText: thousands(vo.cacheWriteTokens ?? 0),
    totalTokensText: input != null ? thousands(total) : "—",
    costUsd: cost,
    billingUsd: billing,
    marginUsd: margin,
    costText: cost != null ? cost.toFixed(4) : "—",
    billingText: billing != null ? billing.toFixed(4) : "—",
    marginText: margin != null && margin > 0 ? margin.toFixed(4) : "—",
    standardPriceText: "—",
    pricingSnapshot: pricingText(detail?.pricingSnapshot),
    httpStatusText: vo.httpStatus != null ? String(vo.httpStatus) : "—",
    ingressMethod: detail?.ingressMethod ?? "POST",
    ingressPath: detail?.ingressPath ?? "—",
    authResult: vo.httpStatus === 401 || vo.httpStatus === 403 ? "失败：AppKey 校验未通过" : "通过（AppKey 有效）",
    clientIp: detail?.clientIp ?? "—",
    alias: vo.alias ?? "—",
    strategyText: route?.strategyLabel ?? "—",
    upstreamEndpoint: detail?.upstreamEndpoint ?? "—",
    upstreamModel: detail?.upstreamModel ?? "—",
    upstreamStatusText: detail?.upstreamStatus ?? "—",
    upstreamLatencyText: detail?.upstreamLatencyMs != null ? `${detail.upstreamLatencyMs}ms` : "—",
    ttftText: detail?.ttftMs != null ? `${detail.ttftMs}ms` : "—",
    retries: detail?.retries ?? 0,
    retriesText: String(detail?.retries ?? 0),
    traceId: detail?.traceId ?? "—",
    error: vo.errorCode
      ? { code: vo.errorCode, type: detail?.errorType ?? "—", message: vo.errorCode }
      : null,
    trace: parseJsonSafe<TraceStep[]>(detail?.routeTrace, []),
    pr: {
      policy: prompt?.policy ?? "masked",
      policyNote: prompt?.policyNote ?? "按策略未留存",
      prompt: prompt?.prompt ?? null,
      response: prompt?.response ?? null,
    },
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function listCallLogs(params: {
  start?: string;
  end?: string;
  status?: string;
  model?: string;
  provider?: string;
  protocol?: string;
  routeGroup?: string;
  email?: string;
  appKey?: string;
}): Promise<CallLogListResult> {
  const upper = (v?: string) => (v && v !== "all" ? v.toUpperCase() : undefined);
  const [page, relations] = await Promise.all([
    backendClient.get<PageResult<RequestLogVo>>("/api/nexmux/request-logs", {
      pageNo: 1,
      pageSize: 5000,
      start: params.start,
      end: params.end,
      status: upper(params.status),
      model: params.model && params.model !== "all" ? params.model : undefined,
      provider: params.provider && params.provider !== "all" ? params.provider : undefined,
      protocol: params.protocol && params.protocol !== "all" ? params.protocol : undefined,
      routeGroup: params.routeGroup && params.routeGroup !== "all" ? params.routeGroup : undefined,
      email: params.email,
      appKey: params.appKey,
    }),
    loadRelations(),
  ]);

  const items = page.records.map((vo) => toRecord(vo, relations));
  return {
    items,
    total: page.total,
    filteredText: items.length.toLocaleString("en-US"),
  };
}

export async function getCallLogById(id: string): Promise<CallLogRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<RequestLogDetailVo>(`/api/nexmux/request-logs/${encodeURIComponent(id)}`),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}
