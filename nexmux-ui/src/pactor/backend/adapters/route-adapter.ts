/**
 * Route 域适配器：nexmux 后端 VO → UI 契约形状（RouteRecord / RouteListResult）。
 *
 * 派生逻辑与 routes-mock 逐字对齐：type 由候选数派生（P0 无 conditional 产出）、
 * operation 由协议映射、模型行经 models/providers 全量内存 join 补名称与供应商摘要。
 * 数据结构依据：core/route/domain/value/{RouteSpec, RouteCandidate}（schema v1 扩展）。
 */

import type {
  RouteListResult,
  RouteModelEntry,
  RouteRecord,
} from "../../mocks/routes-mock";
import { backendClient, fetchAll, type PageResult } from "../client";

// ── 后端 VO 镜像 ──────────────────────────────────────────────

interface RouteCandidateVo {
  modelCode: string;
  weight: number;
  enabled?: boolean;
  priority?: string;
  costRate?: number | null;
  billingRate?: number | null;
}

interface RouteSpecVo {
  alias?: string;
  ingress?: string;
  strategyType?: string;
  candidates?: RouteCandidateVo[];
  filterUnhealthyBindings?: boolean;
  schemaVersion?: number;
  description?: string;
  protocol?: string;
  tags?: string[];
  stickySession?: boolean;
}

interface RouteVo {
  routeId: string;
  routeCode: string;
  alias: string;
  displayName: string;
  status: string;
  routeSpec?: RouteSpecVo;
  publishedAt?: string;
}

interface RouteDetailVo extends RouteVo {
  publishedRouteSpec?: RouteSpecVo;
  publishedBy?: string;
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

interface ModelVo {
  modelId: string;
  modelCode: string;
  displayName: string;
  status: string;
  primaryProviderId?: string;
  primaryProviderModelId?: string;
}

interface ProviderVo {
  providerId: string;
  providerCode: string;
  displayName: string;
}

// ── 常量（与 routes-mock 逐字对齐） ────────────────────────────

const TYPE_LABELS: Record<string, string> = {
  single: "单模型",
  pool: "多模型池",
  conditional: "条件路由",
};

const STATUS_LABELS: Record<string, string> = {
  enabled: "启用",
  disabled: "停用",
  draft: "草稿",
};

const STRATEGY_LABELS: Record<string, string> = {
  WEIGHTED: "加权轮询",
  HASH: "哈希分配",
  PRIORITY: "优先级",
  ROUND_ROBIN: "轮询",
  RANDOM: "随机",
  FIXED: "固定",
};

/** 协议格式 → Ingress Operation（mock PROTOCOL_OPERATIONS） */
const PROTOCOL_OPERATIONS: Record<string, string> = {
  "OpenAI / Chat Completions": "chat.completions",
  "OpenAI / Responses": "responses",
  "Anthropic / Messages": "messages",
  "OpenAI / Embeddings": "embeddings",
};

const TYPE_CHIP_ORDER = ["single", "pool", "conditional"];

// ── 派生工具 ──────────────────────────────────────────────────

function statusOf(backendStatus: string): "enabled" | "disabled" | "draft" {
  if (backendStatus === "PUBLISHED") return "enabled";
  if (backendStatus === "DISABLED") return "disabled";
  return "draft";
}

function typeOf(candidates: RouteCandidateVo[]): "single" | "pool" | "conditional" {
  return candidates.length > 1 ? "pool" : "single";
}

function timeText(iso?: string): string {
  return iso ? iso.slice(0, 16).replace("T", " ") : "";
}

// ── 关联数据（models + providers 全量内存 join） ───────────────

interface RouteRelations {
  modelsByCode: Map<string, ModelVo>;
  providersById: Map<string, ProviderVo>;
}

async function loadRelations(): Promise<RouteRelations> {
  const [models, providers] = await Promise.all([
    fetchAll<ModelVo>("/api/nexmux/models"),
    fetchAll<ProviderVo>("/api/nexmux/providers"),
  ]);
  return {
    modelsByCode: new Map(models.map((m) => [m.modelCode, m])),
    providersById: new Map(providers.map((p) => [p.providerId, p])),
  };
}

function modelEntryOf(candidate: RouteCandidateVo, relations: RouteRelations): RouteModelEntry {
  const model = relations.modelsByCode.get(candidate.modelCode);
  const provider = relations.providersById.get(model?.primaryProviderId ?? "");
  return {
    modelCode: candidate.modelCode,
    modelName: model?.displayName ?? candidate.modelCode,
    providerSummary: provider
      ? `${provider.displayName} · ${model?.primaryProviderModelId ?? candidate.modelCode}`
      : "—",
    weight: candidate.weight,
    priority: (candidate.priority ?? "P0") as RouteModelEntry["priority"],
    costRate: candidate.costRate ?? null,
    billingRate: candidate.billingRate ?? null,
    status: candidate.enabled === false ? "disabled" : "enabled",
  };
}

// ── VO → RouteRecord ──────────────────────────────────────────

function toRecord(
  vo: RouteVo,
  relations: RouteRelations,
  detail?: RouteDetailVo,
): RouteRecord {
  const spec = vo.routeSpec ?? {};
  const candidates = spec.candidates ?? [];
  const models = candidates.map((c) => modelEntryOf(c, relations));
  const type = typeOf(candidates);
  const status = statusOf(vo.status);
  const strategy = (spec.strategyType ?? "FIXED") as RouteRecord["strategy"];
  const strategyLabel = STRATEGY_LABELS[strategy] ?? strategy;
  const path = spec.ingress ?? "";
  const protocol = spec.protocol ?? "";
  const tags = spec.tags ?? [];
  const enabledModels = models.filter((m) => m.status === "enabled");
  const updatedAt = timeText(detail?.updatedAt);

  return {
    code: vo.routeCode,
    name: vo.displayName,
    type,
    typeLabel: TYPE_LABELS[type],
    alias: vo.alias,
    tags,
    description: spec.description ?? "",
    status,
    statusLabel: STATUS_LABELS[status],
    path,
    protocol,
    operation: PROTOCOL_OPERATIONS[protocol] ?? "—",
    strategy,
    strategyLabel,
    stickySession: spec.stickySession ?? false,
    healthFilter: spec.filterUnhealthyBindings ?? true,
    models,
    createdAt: timeText(detail?.createdAt),
    updatedAt,
    operator: detail?.updatedBy ?? "—",
    entrySummary: `${path} · ${protocol}`,
    policySummary: `${models.length} 模型 · ${strategyLabel}`,
    strategySummary: `${strategyLabel} (${strategy})`,
    tagsText: tags.join(" · "),
    enabledModelCount: enabledModels.length,
    weightTotal: enabledModels.reduce((sum, m) => sum + m.weight, 0),
    shortDate: updatedAt.slice(5, 10),
  };
}

// ── counts / filters（构成口径；与 routes-mock 逐字对齐） ──────

function computeCounts(items: RouteRecord[]): RouteListResult["counts"] {
  const enabled = items.filter((r) => r.status === "enabled").length;
  const tagCounts: Record<string, number> = {};
  const tagOrder: string[] = [];
  for (const item of items) {
    for (const tag of item.tags) {
      if (!(tag in tagCounts)) tagOrder.push(tag);
      const key = tag.replace(/-/g, "_");
      tagCounts[key] = (tagCounts[key] ?? 0) + 1;
    }
  }
  const typeStat = (type: string) => {
    const inType = items.filter((r) => r.type === type);
    return { enabled: inType.filter((r) => r.status === "enabled").length, total: inType.length };
  };
  return {
    all: { enabled, total: items.length },
    status: {
      enabled,
      disabled: items.filter((r) => r.status === "disabled").length,
      draft: items.filter((r) => r.status === "draft").length,
    },
    types: {
      single: typeStat("single"),
      pool: typeStat("pool"),
      conditional: typeStat("conditional"),
    },
    tags: tagCounts,
  };
}

function computeFilters(
  counts: RouteListResult["counts"],
  items: RouteRecord[],
): RouteListResult["filters"] {
  const tagOrder: string[] = [];
  for (const item of items) {
    for (const tag of item.tags) {
      if (!tagOrder.includes(tag)) tagOrder.push(tag);
    }
  }
  return {
    status: [
      { value: "all", text: `全部 ${counts.all.enabled}/${counts.all.total}` },
      { value: "enabled", text: `已启用 ${counts.status.enabled}` },
      { value: "disabled", text: `已停用 ${counts.status.disabled}` },
      { value: "draft", text: `草稿 ${counts.status.draft}` },
    ],
    type: [
      { value: "all", text: `全部 ${counts.all.enabled}/${counts.all.total}` },
      ...TYPE_CHIP_ORDER.map((t) => ({
        value: t,
        text: `${TYPE_LABELS[t]} ${counts.types[t as keyof typeof counts.types].enabled}/${counts.types[t as keyof typeof counts.types].total}`,
      })),
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

export async function listRoutes(params: {
  status?: string;
  type?: string;
  tags?: string[];
}): Promise<RouteListResult> {
  const status = params.status && params.status !== "all" ? params.status : undefined;
  const backendStatus =
    status === "enabled" ? "PUBLISHED" : status === "disabled" ? "DISABLED" : status === "draft" ? "DRAFT" : undefined;

  const [page, relations, allForCounts] = await Promise.all([
    backendClient.get<PageResult<RouteVo>>("/api/nexmux/routes", {
      pageNo: 1,
      pageSize: 5000,
      status: backendStatus,
      type: params.type && params.type !== "all" ? params.type : undefined,
      tags: params.tags?.length ? params.tags.join(",") : undefined,
    }),
    loadRelations(),
    fetchAll<RouteVo>("/api/nexmux/routes"),
  ]);

  // 按 updatedAt 降序（mock 语义；列表 VO 无 updatedAt，用 publishedAt/创建序近似）
  const items = page.records.map((vo) => toRecord(vo, relations));
  const allItems = allForCounts.map((vo) => toRecord(vo, relations));
  const counts = computeCounts(allItems);
  return {
    items,
    total: allForCounts.length,
    counts,
    filters: computeFilters(counts, allItems),
  };
}

export async function getRouteByCode(code: string): Promise<RouteRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<RouteDetailVo>(`/api/nexmux/routes/by-code/${encodeURIComponent(code)}`),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}

// ── 写操作 ─────────────────────────────────────────────────────

async function routeIdOf(code: string): Promise<string> {
  const detail = await backendClient.get<RouteDetailVo>(
    `/api/nexmux/routes/by-code/${encodeURIComponent(code)}`,
  );
  return detail.routeId;
}

export async function createRoute(body: Record<string, unknown>): Promise<RouteVo> {
  return backendClient.post<RouteVo>("/api/nexmux/routes", body);
}

export async function updateRoute(code: string, body: Record<string, unknown>): Promise<RouteVo> {
  return backendClient.put<RouteVo>(`/api/nexmux/routes/${await routeIdOf(code)}`, body);
}

export async function publishRoute(
  code: string,
  body: { changeSummary?: string; operatorId?: string },
): Promise<RouteVo> {
  return backendClient.post<RouteVo>(`/api/nexmux/routes/${await routeIdOf(code)}/publish`, body);
}

export async function setRouteEnabled(code: string, enabled: boolean): Promise<RouteVo> {
  const id = await routeIdOf(code);
  return backendClient.post<RouteVo>(`/api/nexmux/routes/${id}/${enabled ? "enable" : "disable"}`);
}
