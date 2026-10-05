/**
 * ClientTemplate 域适配器：nexmux 后端 VO → UI 契约形状（ClientTemplateRecord）。
 * 派生（validityLabel / quotaSummary / policySummary 等）与 client-templates-mock 逐字对齐。
 */

import type { AuthRoute, QuotaRule, RateLimitRule } from "../contracts";
import type {
  ClientTemplateListResult,
  ClientTemplateRecord,
} from "../contracts";
import type { ClientRecord } from "../contracts";
import { backendClient, fetchAll, type PageResult } from "../client";
import {
  deriveQuota,
  deriveRateLimit,
  parseSpec,
  quotaSummaryOf,
  rateLimitSummaryOf,
} from "./quota-shared";
import { getClientByCode } from "./client-adapter";
import type {
  ClientTemplateDetailVo,
  ClientTemplateVo,
  ClientVo,
  RouteVo,
} from "../types";

// ── 派生工具 ──────────────────────────────────────────────────

const STATUS_LABELS: Record<string, string> = {
  enabled: "启用",
  disabled: "停用",
};

function timeText(iso?: string): string {
  return iso ? iso.slice(0, 16).replace("T", " ") : "";
}

function fixedDateText(iso?: string): string {
  return iso ? iso.slice(0, 10) : "";
}

function validityLabelOf(type?: string, days?: number, fixedDate?: string): string {
  if (type === "days") return `创建后 ${days} 天`;
  if (type === "fixed") return `固定至 ${fixedDate}`;
  return "永不过期";
}

// ── 关联数据 ───────────────────────────────────────────────────

interface TemplateRelations {
  routesById: Map<string, RouteVo>;
}

async function loadRelations(): Promise<TemplateRelations> {
  const routes = await fetchAll<RouteVo>("/api/nexmux/routes");
  return { routesById: new Map(routes.map((r) => [r.routeId, r])) };
}

// ── VO → ClientTemplateRecord ─────────────────────────────────

function toRecord(
  vo: ClientTemplateVo | ClientTemplateDetailVo,
  relations: TemplateRelations,
  detail?: ClientTemplateDetailVo,
): ClientTemplateRecord {
  const authRoutes: AuthRoute[] = (vo.authorizedRouteIds ?? [])
    .map((id) => relations.routesById.get(id))
    .filter((r): r is RouteVo => !!r)
    .map((r) => ({
      code: r.routeCode,
      name: r.displayName,
      alias: r.alias,
      typeLabel: (r.routeSpec?.candidates?.length ?? 0) > 1 ? "多模型池" : "单模型",
      path: r.routeSpec?.ingress ?? "",
    }));

  const quotas = parseSpec<QuotaRule>(vo.quotaSpec).map(deriveQuota);
  const rateLimits = parseSpec<RateLimitRule>(vo.rateLimitSpec).map((r) =>
    deriveRateLimit(r, (r.scopeRoutes ?? [])
      .map((id) => relations.routesById.get(id)?.displayName)
      .filter((n): n is string => !!n)),
  );
  const quotaSummary = quotaSummaryOf(quotas);
  const rateLimitSummary = rateLimitSummaryOf(rateLimits);
  const status = vo.status === "ENABLED" ? "enabled" : "disabled";
  const tags = detail?.tags ?? parseSpec<string>(Array.isArray(vo.tags) ? undefined : vo.tags);
  const validityFixedDate = fixedDateText(vo.validTo) || undefined;

  return {
    code: vo.templateCode,
    name: vo.displayName,
    tags,
    description: detail?.description ?? "",
    status: status as ClientTemplateRecord["status"],
    statusLabel: STATUS_LABELS[status],
    validityType: (vo.validityType ?? "never") as ClientTemplateRecord["validityType"],
    validityDays: vo.validityDays,
    validityFixedDate,
    validityLabel: validityLabelOf(vo.validityType, vo.validityDays, validityFixedDate),
    authRoutes,
    quotas,
    rateLimits,
    createdClientCount: vo.createdClientCount ?? 0,
    createdAt: timeText(detail?.createdAt),
    updatedAt: timeText(detail?.updatedAt),
    operator: detail?.updatedBy ?? "—",
    routeCount: authRoutes.length,
    quotaSummary,
    rateLimitSummary,
    tagsText: tags.join(" · "),
    policySummary: `${authRoutes.length} 路由 · ${quotaSummary} · ${rateLimitSummary}`,
  };
}

// ── counts / filters（与 client-templates-mock 逐字对齐） ──────

function computeCounts(items: ClientTemplateRecord[]): ClientTemplateListResult["counts"] {
  const tagCounts: Record<string, number> = {};
  for (const item of items) {
    for (const tag of item.tags) {
      const key = tag.replace(/-/g, "_");
      tagCounts[key] = (tagCounts[key] ?? 0) + 1;
    }
  }
  return {
    all: {
      enabled: items.filter((t) => t.status === "enabled").length,
      total: items.length,
    },
    status: {
      enabled: items.filter((t) => t.status === "enabled").length,
      disabled: items.filter((t) => t.status === "disabled").length,
    },
    tags: tagCounts,
  };
}

function computeFilters(
  counts: ClientTemplateListResult["counts"],
  items: ClientTemplateRecord[],
): ClientTemplateListResult["filters"] {
  const tagOrder: string[] = [];
  for (const item of items) {
    for (const tag of item.tags) {
      if (!tagOrder.includes(tag)) tagOrder.push(tag);
    }
  }
  return {
    status: [
      { value: "all", text: `全部 ${counts.all.enabled}/${counts.all.total}` },
      { value: "enabled", text: `启用 ${counts.status.enabled}` },
      { value: "disabled", text: `停用 ${counts.status.disabled}` },
    ],
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

export async function listClientTemplates(params: {
  status?: string;
  keyword?: string;
  tags?: string[];
  page?: number;
  pageSize?: number;
}): Promise<ClientTemplateListResult> {
  const status = params.status && params.status !== "all" ? params.status : undefined;
  const pageNo = params.page ?? 1;
  const pageSize = params.pageSize ?? 50;
  const [page, relations, allForCounts] = await Promise.all([
    backendClient.get<PageResult<ClientTemplateVo>>("/api/nexmux/client-templates", {
      pageNo,
      pageSize,
      status: status?.toUpperCase(),
      keyword: params.keyword || undefined,
      tags: params.tags?.length ? params.tags.join(",") : undefined,
    }),
    loadRelations(),
    fetchAll<ClientTemplateVo>("/api/nexmux/client-templates"),
  ]);

  const items = page.records.map((vo) => toRecord(vo, relations));
  const allItems = allForCounts.map((vo) => toRecord(vo, relations));
  const counts = computeCounts(allItems);
  return {
    items,
    // total 为筛选后总数（后端 PageResult）；构成口径由 counts.all.total 承担
    total: page.total,
    pageNo,
    pageSize,
    pages: page.pages,
    counts,
    filters: computeFilters(counts, allItems),
  };
}

export async function getClientTemplateByCode(
  code: string,
): Promise<ClientTemplateRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<ClientTemplateDetailVo>(
      `/api/nexmux/client-templates/by-code/${encodeURIComponent(code)}`,
    ),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}

/** 基于模板创建的客户端（复用 client-adapter 的记录整形） */
export async function clientsCreatedByTemplate(code: string): Promise<(ClientRecord | null)[]> {
  const detail = await backendClient.get<ClientTemplateDetailVo>(
    `/api/nexmux/client-templates/by-code/${encodeURIComponent(code)}`,
  );
  const clients = await backendClient.get<ClientVo[]>(
    `/api/nexmux/client-templates/${detail.templateId}/created-clients`,
  );
  return Promise.all(clients.map((c) => getClientByCode(c.clientCode)));
}

// ── 写操作 ─────────────────────────────────────────────────────

async function templateIdOf(code: string): Promise<string> {
  const detail = await backendClient.get<ClientTemplateDetailVo>(
    `/api/nexmux/client-templates/by-code/${encodeURIComponent(code)}`,
  );
  return detail.templateId;
}

export async function createClientTemplate(
  body: Record<string, unknown>,
): Promise<ClientTemplateVo> {
  return backendClient.post<ClientTemplateVo>("/api/nexmux/client-templates", body);
}

export async function updateClientTemplate(
  code: string,
  body: Record<string, unknown>,
): Promise<ClientTemplateVo> {
  return backendClient.put<ClientTemplateVo>(
    `/api/nexmux/client-templates/${await templateIdOf(code)}`,
    body,
  );
}

export async function setClientTemplateEnabled(
  code: string,
  enabled: boolean,
): Promise<ClientTemplateVo> {
  const id = await templateIdOf(code);
  return backendClient.post<ClientTemplateVo>(
    `/api/nexmux/client-templates/${id}/${enabled ? "enable" : "disable"}`,
  );
}
