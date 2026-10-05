/**
 * Client 域适配器：nexmux 后端 VO → UI 契约形状（ClientRecord / ClientListResult）。
 *
 * 关键点：
 * - expired 为展示层派生：ENABLED 且 expiresAt 已过 → expired（后端无定时任务）；
 * - authRoutes 由 authorizedRouteIds 关联 routes 全量补快照字段；
 * - 配额用量字段（usageUsed/Percent/nextResetAt）Runtime 未建，统一 null；
 * - appKeySummary / quotaSummary / rateLimitSummary 等派生文案在 BFF。
 */

import type { AuthRoute, QuotaRule, RateLimitRule } from "../contracts";
import type {
  ClientListResult,
  ClientQuotaRule,
  ClientRecord,
} from "../contracts";
import { backendClient, fetchAll, type PageResult } from "../client";
import {
  deriveQuota,
  deriveRateLimit,
  parseSpec,
  quotaSummaryOf,
  rateLimitSummaryOf,
} from "./quota-shared";
import type { ClientDetailVo, ClientTemplateVo, ClientVo, RouteVo } from "../types";

// ── 派生工具 ──────────────────────────────────────────────────

const STATUS_LABELS: Record<string, string> = {
  enabled: "启用",
  disabled: "停用",
  expired: "已过期",
};

/** 展示状态：ENABLED 且已过期 → expired（展示层语义） */
function statusOf(entityStatus: string, expiresAt?: string): "enabled" | "disabled" | "expired" {
  if (entityStatus === "ENABLED") {
    if (expiresAt && new Date(expiresAt).getTime() < Date.now()) return "expired";
    return "enabled";
  }
  return "disabled";
}

function timeText(iso?: string): string {
  return iso ? iso.slice(0, 16).replace("T", " ") : "";
}

function parseTags(text?: string | string[]): string[] {
  return Array.isArray(text) ? text : parseSpec<string>(text);
}

// ── 关联数据 ───────────────────────────────────────────────────

interface ClientRelations {
  routesById: Map<string, RouteVo>;
  templatesById: Map<string, ClientTemplateVo>;
}

async function loadRelations(): Promise<ClientRelations> {
  const [routes, templates] = await Promise.all([
    fetchAll<RouteVo>("/api/nexmux/routes"),
    fetchAll<ClientTemplateVo>("/api/nexmux/client-templates"),
  ]);
  return {
    routesById: new Map(routes.map((r) => [r.routeId, r])),
    templatesById: new Map(templates.map((t) => [t.templateId, t])),
  };
}

function authRouteOf(route: RouteVo): AuthRoute {
  const candidates = route.routeSpec?.candidates ?? [];
  return {
    code: route.routeCode,
    name: route.displayName,
    alias: route.alias,
    typeLabel: candidates.length > 1 ? "多模型池" : "单模型",
    path: route.routeSpec?.ingress ?? "",
  };
}

// ── VO → ClientRecord ─────────────────────────────────────────

function toRecord(
  vo: ClientVo | ClientDetailVo,
  relations: ClientRelations,
  detail?: ClientDetailVo,
): ClientRecord {
  const authRoutes = (vo.authorizedRouteIds ?? [])
    .map((id) => relations.routesById.get(id))
    .filter((r): r is RouteVo => !!r)
    .map(authRouteOf);

  const quotas: ClientQuotaRule[] = parseSpec<QuotaRule>(vo.quotaSpec).map((q) => ({
    ...deriveQuota(q),
    usageUsed: undefined,
    usageUsedText: undefined,
    usagePercent: undefined,
    nextResetAt: undefined,
  }));
  const rateLimits = parseSpec<RateLimitRule>(vo.rateLimitSpec).map((r) =>
    deriveRateLimit(r, (r.scopeRoutes ?? [])
      .map((id) => relations.routesById.get(id)?.displayName)
      .filter((n): n is string => !!n)),
  );

  const status = statusOf(vo.status ?? "", vo.expiresAt);
  const rateLimitSummary = rateLimitSummaryOf(rateLimits);
  const expiresAt = timeText(vo.expiresAt);
  const template = relations.templatesById.get(vo.sourceTemplateId ?? "");

  return {
    code: vo.clientCode,
    name: vo.displayName,
    tags: detail?.tags ?? parseTags(vo.tags),
    description: "",
    status,
    statusLabel: STATUS_LABELS[status],
    expiresAt,
    appKeyId: vo.appKeyId ?? "",
    secretLastRotatedAt: timeText(vo.appKeyRotatedAt),
    templateCode: template?.templateCode,
    templateName: template?.displayName,
    authRoutes,
    quotas,
    rateLimits,
    createdAt: timeText(detail?.createdAt),
    updatedAt: timeText(detail?.updatedAt),
    operator: detail?.updatedBy ?? "—",
    routeCount: authRoutes.length,
    quotaSummary: quotaSummaryOf(quotas),
    rateLimitSummary,
    tagsText: (detail?.tags ?? parseTags(vo.tags)).join(" · "),
    appKeySummary: expiresAt
      ? `${vo.appKeyId} · 到期 ${expiresAt.slice(0, 10)} · ${authRoutes.length} 路由 · ${rateLimitSummary}`
      : `${vo.appKeyId} · 永不过期 · ${authRoutes.length} 路由 · ${rateLimitSummary}`,
  };
}

// ── counts / filters（构成口径；与 clients-mock 逐字对齐） ────

function computeCounts(items: ClientRecord[]): ClientListResult["counts"] {
  const tagCounts: Record<string, number> = {};
  for (const item of items) {
    for (const tag of item.tags) {
      const key = tag.replace(/-/g, "_");
      tagCounts[key] = (tagCounts[key] ?? 0) + 1;
    }
  }
  return {
    all: {
      enabled: items.filter((c) => c.status === "enabled").length,
      total: items.length,
    },
    status: {
      enabled: items.filter((c) => c.status === "enabled").length,
      disabled: items.filter((c) => c.status === "disabled").length,
      expired: items.filter((c) => c.status === "expired").length,
    },
    tags: tagCounts,
  };
}

function computeFilters(
  counts: ClientListResult["counts"],
  items: ClientRecord[],
): ClientListResult["filters"] {
  const tagOrder: string[] = [];
  for (const item of items) {
    for (const tag of item.tags) {
      if (!tagOrder.includes(tag)) tagOrder.push(tag);
    }
  }
  return {
    tags: [
      { value: "all", text: `全部 ${counts.all.total}` },
      ...tagOrder.map((t) => ({
        value: t,
        text: `${t} ${counts.tags[t.replace(/-/g, "_")] ?? 0}`,
      })),
    ],
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function listClients(params: {
  status?: string;
  name?: string;
  code?: string;
  tags?: string[];
  page?: number;
  pageSize?: number;
}): Promise<ClientListResult> {
  const status = params.status && params.status !== "all" ? params.status : undefined;
  // expired 是展示态，ENABLED 全量下发后由 BFF 判定
  const backendStatus =
    status === "enabled" || status === "expired" ? "ENABLED" : status === "disabled" ? "DISABLED" : undefined;
  const pageNo = params.page ?? 1;
  const pageSize = params.pageSize ?? 50;
  // enabled/expired 为 BFF 展示态过滤：需拉全量 ENABLED 先过滤再切片，total = 过滤后长度
  const localFilter = status === "enabled" || status === "expired";

  const [page, relations, allForCounts] = await Promise.all([
    backendClient.get<PageResult<ClientVo>>("/api/nexmux/clients", {
      pageNo: localFilter ? 1 : pageNo,
      pageSize: localFilter ? 5000 : pageSize,
      status: backendStatus,
      name: params.name || undefined,
      code: params.code || undefined,
      tags: params.tags?.length ? params.tags.join(",") : undefined,
    }),
    loadRelations(),
    fetchAll<ClientVo>("/api/nexmux/clients"),
  ]);

  let items = page.records.map((vo) => toRecord(vo, relations));
  let total = page.total;
  let pages = page.pages;
  if (localFilter) {
    if (status === "expired") items = items.filter((c) => c.status === "expired");
    if (status === "enabled") items = items.filter((c) => c.status === "enabled");
    total = items.length;
    pages = Math.ceil(total / pageSize);
    items = items.slice((pageNo - 1) * pageSize, pageNo * pageSize);
  }

  const allItems = allForCounts.map((vo) => toRecord(vo, relations));
  const counts = computeCounts(allItems);
  return {
    items,
    // total 为筛选后总数；构成口径由 counts.all.total 承担
    total,
    pageNo,
    pageSize,
    pages,
    counts,
    filters: computeFilters(counts, allItems),
  };
}

export async function getClientByCode(code: string): Promise<ClientRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<ClientDetailVo>(`/api/nexmux/clients/by-code/${encodeURIComponent(code)}`),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}

// ── 写操作 ─────────────────────────────────────────────────────

async function clientIdOf(code: string): Promise<string> {
  const detail = await backendClient.get<ClientDetailVo>(
    `/api/nexmux/clients/by-code/${encodeURIComponent(code)}`,
  );
  return detail.clientId;
}

export async function createClient(
  body: Record<string, unknown>,
): Promise<{ client: ClientVo; appKeyPlaintext: string }> {
  return backendClient.post("/api/nexmux/clients", body);
}

export async function updateClient(code: string, body: Record<string, unknown>): Promise<ClientVo> {
  return backendClient.put<ClientVo>(`/api/nexmux/clients/${await clientIdOf(code)}`, body);
}

export async function setClientEnabled(code: string, enabled: boolean): Promise<ClientVo> {
  const id = await clientIdOf(code);
  return backendClient.post<ClientVo>(`/api/nexmux/clients/${id}/${enabled ? "enable" : "disable"}`);
}

export async function rotateClientAppKey(
  code: string,
): Promise<{ clientId: string; appKeyPlaintext: string; appKeyVersion: number }> {
  return backendClient.post(`/api/nexmux/clients/${await clientIdOf(code)}/rotate-app-key`);
}

export async function updateClientRouteGrants(code: string, routeIds: string[]): Promise<ClientVo> {
  return backendClient.put<ClientVo>(
    `/api/nexmux/clients/${await clientIdOf(code)}/route-grants`,
    { routeIds },
  );
}
