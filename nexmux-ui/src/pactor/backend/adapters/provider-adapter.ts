/**
 * Provider 域适配器：nexmux 后端 VO → UI 契约形状（ProviderRecord / ProviderListResult）。
 *
 * 职责（设计 §7.2）：R/PageResult 解包之外的「展示整形」——派生文案、icon 白名单
 * 映射、counts/filters 构成口径计算、provider↔route 关联统计，全部留在 BFF。
 * 后端 VO 字段见 nexmux-kernel core/provider/domain/vo。
 */

import type {
  ProviderListParams,
  ProviderListResult,
  ProviderModel,
  ProviderRecord,
  PullModelRow,
} from "../../mocks/providers-mock";
import { backendClient, fetchAll, type PageResult } from "../client";

// ── 后端 VO 镜像（仅适配所需字段） ────────────────────────────

interface ProviderVo {
  providerId: string;
  providerCode: string;
  displayName: string;
  providerType: string;
  defaultProtocol: string;
  vendorCode: string;
  sourceVendorCode: string;
  iconUri?: string;
  baseUrl: string;
  region?: string;
  timeoutMs?: number;
  status: string;
  keyStatus: string;
  modelCount: number;
}

interface ProviderModelRowVo {
  modelCode: string;
  displayName: string;
  modelType: string;
  providerModelId: string;
  status: string;
}

interface ProviderDetailVo extends Omit<ProviderVo, "modelCount"> {
  proxy?: string;
  description?: string;
  capabilityExtension?: string;
  keyVersion?: number;
  rotatedAt?: string;
  models: ProviderModelRowVo[];
  createdAt?: string;
  updatedAt?: string;
  updatedBy?: string;
}

interface ModelVo {
  modelCode: string;
  primaryProviderId: string;
  status: string;
}

interface RouteVo {
  status: string;
  routeSpec?: { candidates?: { modelCode: string }[] };
}

interface UpstreamModelVo {
  providerModelId: string;
  existsLocally: boolean;
}

interface ConnectionTestVo {
  ok: boolean;
  latencyMs: number;
  detail: string;
}

/** vendorCode → Icon 白名单图标名（前端 Icon 组件白名单取值） */
const VENDOR_ICONS: Record<string, string> = {
  openai: "zap",
  anthropic: "brain",
  gemini: "star",
  dashscope: "cloud",
};

/** 已知厂商的 Chip 圆点色（其余厂商无 dot；catalog 有 40+ 厂商，chips 动态生成） */
const VENDOR_DOTS: Record<string, string> = {
  openai: "#3b82f6",
  anthropic: "#b45309",
  gemini: "#1d4ed8",
  dashscope: "#8b5cf6",
};

// ── 派生工具 ──────────────────────────────────────────────────

function iconOf(vendorCode: string): string {
  return VENDOR_ICONS[vendorCode] ?? "zap";
}

function statusOf(backendStatus: string): "enabled" | "disabled" {
  return backendStatus === "ENABLED" ? "enabled" : "disabled";
}

/** capabilityExtension JSON → 能力标签数组（容忍数组 / 对象 / 非法文本） */
function capabilitiesOf(capabilityExtension?: string): string[] {
  if (!capabilityExtension) return [];
  try {
    const parsed: unknown = JSON.parse(capabilityExtension);
    if (Array.isArray(parsed)) return parsed.filter((v): v is string => typeof v === "string");
    if (parsed && typeof parsed === "object") return Object.keys(parsed);
  } catch {
    /* 非 JSON 文本时按空处理 */
  }
  return [];
}

function maskedKeyOf(keyStatus: string, keyVersion?: number): string {
  if (keyStatus === "configured") return `••••••••${keyVersion ? ` (v${keyVersion})` : ""}`;
  if (keyStatus === "pending") return "待配置";
  return "—";
}

/** ISO 时间 → 展示格式（yyyy-MM-dd HH:mm） */
function timeText(iso?: string): string {
  return iso ? iso.slice(0, 16).replace("T", " ") : "";
}

/** route_spec JSON → 引用的 modelCode 集合 */
function routeModelCodes(route: RouteVo): string[] {
  return (route.routeSpec?.candidates ?? []).map((c) => c.modelCode);
}

// ── 关联统计（P0 数据量小：models/routes 全量内存关联） ───────

interface ProviderRelation {
  /** providerCode → 其下模型 code 集合 */
  modelsByProvider: Map<string, { code: string; enabled: boolean }[]>;
  /** providerCode → { total, enabled } 路由统计 */
  routesByProvider: Map<string, { total: number; enabled: number }>;
  /** modelCode → 引用它的路由数 */
  routeCountByModel: Map<string, number>;
}

async function loadRelations(): Promise<ProviderRelation> {
  const [models, routes] = await Promise.all([
    fetchAll<ModelVo>("/api/nexmux/models"),
    fetchAll<RouteVo>("/api/nexmux/routes"),
  ]);

  const modelsByProvider = new Map<string, { code: string; enabled: boolean }[]>();
  for (const model of models) {
    const list = modelsByProvider.get(model.primaryProviderId) ?? [];
    list.push({ code: model.modelCode, enabled: model.status === "ENABLED" });
    modelsByProvider.set(model.primaryProviderId, list);
  }

  const modelToProviderId = new Map<string, string>();
  for (const model of models) modelToProviderId.set(model.modelCode, model.primaryProviderId);

  const routesByProviderId = new Map<string, { total: number; enabled: number }>();
  const routeCountByModel = new Map<string, number>();
  for (const route of routes) {
    const providerIds = new Set(
      routeModelCodes(route)
        .map((code) => modelToProviderId.get(code))
        .filter((id): id is string => !!id),
    );
    for (const providerId of providerIds) {
      const stat = routesByProviderId.get(providerId) ?? { total: 0, enabled: 0 };
      stat.total += 1;
      if (route.status === "PUBLISHED") stat.enabled += 1;
      routesByProviderId.set(providerId, stat);
    }
    for (const code of routeModelCodes(route)) {
      routeCountByModel.set(code, (routeCountByModel.get(code) ?? 0) + 1);
    }
  }

  return { modelsByProvider, routesByProvider: routesByProviderId, routeCountByModel };
}

function routeSummaryOf(routes: { total: number; enabled: number }): string {
  return routes.total === 0 ? "未关联路由" : `${routes.total} 条路由 · ${routes.enabled} 条启用`;
}

// ── VO → ProviderRecord ───────────────────────────────────────

function toRecord(
  vo: ProviderVo | ProviderDetailVo,
  relations: ProviderRelation,
  detail?: ProviderDetailVo,
): ProviderRecord {
  const ownModels = relations.modelsByProvider.get(vo.providerId) ?? [];
  const routes = relations.routesByProvider.get(vo.providerId) ?? { total: 0, enabled: 0 };
  const isDetail = (v: ProviderVo | ProviderDetailVo): v is ProviderDetailVo => "models" in v;
  const detailVo = detail ?? (isDetail(vo) ? vo : undefined);

  const models: ProviderModel[] = (detailVo?.models ?? []).map((m) => ({
    code: m.modelCode,
    name: m.displayName,
    type: m.modelType,
    providerModelId: m.providerModelId,
    pricing: "—",
    status: m.status === "ENABLED" ? "ONLINE" : "OFFLINE",
    routes: relations.routeCountByModel.get(m.modelCode) ?? 0,
  }));
  const enabledModelCount = detailVo
    ? models.filter((m) => m.status === "ONLINE").length
    : ownModels.filter((m) => m.enabled).length;

  return {
    code: vo.providerCode,
    name: vo.displayName,
    displayName: vo.displayName,
    icon: iconOf(vo.vendorCode),
    providerType: vo.providerType,
    protocol: vo.defaultProtocol,
    family: vo.vendorCode as ProviderRecord["family"],
    status: statusOf(vo.status),
    keyStatus: (vo.keyStatus ?? "none") as ProviderRecord["keyStatus"],
    baseUrl: vo.baseUrl,
    region: vo.region ?? "",
    timeout: vo.timeoutMs ?? 0,
    proxy: detailVo?.proxy ?? "",
    maskedKey: maskedKeyOf(vo.keyStatus ?? "none", detailVo?.keyVersion),
    lastTest: null,
    capabilities: capabilitiesOf(detailVo?.capabilityExtension),
    routes,
    routeSummary: routeSummaryOf(routes),
    models,
    modelCount: detailVo ? models.length : ownModels.length || (vo as ProviderVo).modelCount || 0,
    enabledModelCount,
    description: detailVo?.description ?? "",
    capabilityExtension: detailVo?.capabilityExtension ?? "",
    createdAt: timeText(detailVo?.createdAt),
    updatedAt: timeText(detailVo?.updatedAt),
    operator: detailVo?.updatedBy ?? "—",
  };
}

// ── counts / filters（构成口径：全量，不随筛选变化） ──────────

function computeCounts(items: ProviderRecord[]): ProviderListResult["counts"] {
  const enabled = items.filter((p) => p.status === "enabled").length;
  // 厂商族动态聚合（catalog 40+ 厂商，不再限静态 4 族）
  const types: Record<string, { enabled: number; total: number }> = {};
  for (const item of items) {
    const stat = types[item.family] ?? { enabled: 0, total: 0 };
    stat.total += 1;
    if (item.status === "enabled") stat.enabled += 1;
    types[item.family] = stat;
  }
  return {
    all: { enabled, total: items.length },
    status: {
      enabled,
      disabled: items.filter((p) => p.status === "disabled").length,
      pending: items.filter((p) => p.keyStatus === "pending").length,
      none: items.filter((p) => p.keyStatus === "none").length,
    },
    types,
  };
}

function computeFilters(
  counts: ProviderListResult["counts"],
  items: ProviderRecord[],
): ProviderListResult["filters"] {
  // 厂商 label 取该族第一个实例的 displayName；按总数降序排列
  const labelByFamily = new Map<string, string>();
  for (const item of items) {
    if (!labelByFamily.has(item.family)) labelByFamily.set(item.family, item.name);
  }
  const typeChips = Object.entries(counts.types)
    .sort((a, b) => b[1].total - a[1].total)
    .map(([family, stat]) => ({
      value: family,
      text: `${labelByFamily.get(family) ?? family} ${stat.enabled}/${stat.total}`,
      ...(VENDOR_DOTS[family] ? { dot: VENDOR_DOTS[family] } : {}),
    }));
  return {
    status: [
      { value: "all", text: `全部 ${counts.all.enabled}/${counts.all.total}` },
      { value: "enabled", text: `已启用 ${counts.status.enabled}`, dot: "#22c55e" },
      { value: "disabled", text: `已停用 ${counts.status.disabled}`, dot: "#9ca3af" },
      { value: "pending", text: `待填密钥 ${counts.status.pending}`, dot: "#f59e0b" },
      { value: "none", text: `无密钥 ${counts.status.none}`, dot: "#ef4444" },
    ],
    types: typeChips,
  };
}

// ── 对外服务（与 services.ts handler 对齐） ───────────────────

export async function listProviders(params: ProviderListParams): Promise<ProviderListResult> {
  // 前端 status 快筛跨两维（mock matchesStatus）：enabled/disabled → status；
  // pending/none → keyStatus（设计 §6.3 拆分约定）
  const status = params.status ?? "all";
  const backendStatus = status === "enabled" || status === "disabled" ? status.toUpperCase() : undefined;
  const keyStatus = status === "pending" || status === "none" ? status : undefined;

  const [page, relations, allForCounts] = await Promise.all([
    backendClient.get<PageResult<ProviderVo>>("/api/nexmux/providers", {
      pageNo: 1,
      pageSize: 5000,
      keyword: params.keyword || undefined,
      status: backendStatus,
      keyStatus,
      types: params.types?.length ? params.types.join(",") : undefined,
    }),
    loadRelations(),
    // counts 构成口径需要全量（不受当前筛选影响）
    fetchAll<ProviderVo>("/api/nexmux/providers"),
  ]);

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

export async function getProviderByCode(code: string): Promise<ProviderRecord | null> {
  const [detail, relations] = await Promise.all([
    backendClient.get<ProviderDetailVo>(`/api/nexmux/providers/by-code/${encodeURIComponent(code)}`),
    loadRelations(),
  ]);
  return toRecord(detail, relations, detail);
}

export async function pullProviderModels(code: string): Promise<PullModelRow[]> {
  const detail = await backendClient.get<ProviderDetailVo>(
    `/api/nexmux/providers/by-code/${encodeURIComponent(code)}`,
  );
  const rows = await backendClient.post<UpstreamModelVo[]>(
    `/api/nexmux/providers/${detail.providerId}/pull-models`,
  );
  return rows.map((row) => ({
    id: row.providerModelId,
    name: row.providerModelId,
    local: row.existsLocally ? "已存在" : "新增",
  }));
}

export async function testProviderConnection(
  code: string,
): Promise<{ ok: boolean; latencyMs: number; detail: string }> {
  const detail = await backendClient.get<ProviderDetailVo>(
    `/api/nexmux/providers/by-code/${encodeURIComponent(code)}`,
  );
  return backendClient.post<ConnectionTestVo>(
    `/api/nexmux/providers/${detail.providerId}/connection-test`,
  );
}

// ── 写操作（BFF 写通道；code → providerId 解析） ──────────────

export interface ProviderWriteBody {
  [key: string]: unknown;
}

async function providerIdOf(code: string): Promise<string> {
  const detail = await backendClient.get<ProviderDetailVo>(
    `/api/nexmux/providers/by-code/${encodeURIComponent(code)}`,
  );
  return detail.providerId;
}

export async function createProvider(body: ProviderWriteBody): Promise<ProviderVo> {
  return backendClient.post<ProviderVo>("/api/nexmux/providers", body);
}

export async function updateProvider(code: string, body: ProviderWriteBody): Promise<ProviderVo> {
  return backendClient.put<ProviderVo>(`/api/nexmux/providers/${await providerIdOf(code)}`, body);
}

export async function setProviderEnabled(code: string, enabled: boolean): Promise<ProviderVo> {
  const id = await providerIdOf(code);
  return backendClient.post<ProviderVo>(`/api/nexmux/providers/${id}/${enabled ? "enable" : "disable"}`);
}
