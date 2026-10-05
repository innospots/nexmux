/**
 * 真实后端实现注册表（B1：Provider 域）。
 *
 * 与 src/pactor/services.ts 的 mock 注册表同签名；services.ts 按
 * backendConfig().enabled 逐服务灰度切换（设计 §2 成功标准 3）。
 *
 * pactorMutations：写通道映射表，key 为 "<METHOD> <path pattern>"，
 * 由 app/api/data/[...parts]/route.ts 解析（设计 §7.2 写映射表）。
 */

import {
  createProvider,
  getProviderByCode,
  listProviders,
  pullProviderModels,
  setProviderEnabled,
  testProviderConnection,
  updateProvider,
} from "./adapters/provider-adapter";
import type { ProviderCreateRequest, ProviderUpdateRequest } from "./types";
import {
  createModel,
  getModelByCode,
  invokeModelTest,
  listModels,
  setModelEnabled,
  updateModel,
} from "./adapters/model-adapter";
import {
  createRoute,
  getRouteByCode,
  listRoutes,
  publishRoute,
  setRouteEnabled,
  updateRoute,
} from "./adapters/route-adapter";
import {
  createClient,
  getClientByCode,
  listClients,
  rotateClientAppKey,
  setClientEnabled,
  updateClient,
  updateClientRouteGrants,
} from "./adapters/client-adapter";
import {
  clientsCreatedByTemplate,
  createClientTemplate,
  getClientTemplateByCode,
  listClientTemplates,
  setClientTemplateEnabled,
  updateClientTemplate,
} from "./adapters/client-template-adapter";
import { getCallLogById, listCallLogs } from "./adapters/call-log-adapter";
import { getCostById, listCosts } from "./adapters/cost-adapter";
import { getAuditById, listAuditOperators, listAudits } from "./adapters/audit-adapter";
import { fetchAll } from "./client";
import type { ProviderVo } from "./types";

function str(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function strList(value: unknown): string[] | undefined {
  if (Array.isArray(value)) {
    return value.filter((v): v is string => typeof v === "string" && v !== "");
  }
  if (typeof value === "string") return value.split(",").filter(Boolean);
  return undefined;
}

function opt(value: unknown): string | undefined {
  return typeof value === "string" && value !== "" ? value : undefined;
}

/** 分页参数：字符串 query 转数字，非正整数落缺省（undefined → adapter 默认 1/50） */
function num(value: unknown): number | undefined {
  const n = typeof value === "string" ? Number(value) : typeof value === "number" ? value : NaN;
  return Number.isInteger(n) && n >= 1 ? n : undefined;
}

/** 读服务（与 mock handler 同签名） */
export const realServices = {
  "provider.list": (params: Record<string, unknown>) =>
    listProviders({
      keyword: str(params.keyword),
      status: str(params.status) || "all",
      types: strList(params.types),
      page: num(params.page),
      pageSize: num(params.pageSize),
    }),
  "provider.detail": (params: Record<string, unknown>) => getProviderByCode(str(params.code)),
  "provider.pullModels": (params: Record<string, unknown>) => pullProviderModels(str(params.code)),
  /** 表单 Provider 下拉选项（模型新建/编辑页用）：{label, value}[] */
  "provider.options": async () => {
    const providers = await fetchAll<ProviderVo>("/api/nexmux/providers");
    return providers.map((p) => ({ label: p.displayName, value: p.providerCode }));
  },

  "model.list": (params: Record<string, unknown>) =>
    listModels({
      type: str(params.type) || "all",
      providers: strList(params.providers),
      page: num(params.page),
      pageSize: num(params.pageSize),
    }),
  "model.detail": (params: Record<string, unknown>) => getModelByCode(str(params.code)),

  "route.list": (params: Record<string, unknown>) =>
    listRoutes({
      status: str(params.status) || "all",
      type: str(params.type) || "all",
      tags: strList(params.tags),
      page: num(params.page),
      pageSize: num(params.pageSize),
    }),
  "route.detail": (params: Record<string, unknown>) => getRouteByCode(str(params.code)),

  "client.list": (params: Record<string, unknown>) =>
    listClients({
      status: str(params.status) || "all",
      name: str(params.name),
      code: str(params.code),
      tags: strList(params.tags),
      page: num(params.page),
      pageSize: num(params.pageSize),
    }),
  "client.detail": (params: Record<string, unknown>) => getClientByCode(str(params.code)),

  "clientTemplate.list": (params: Record<string, unknown>) =>
    listClientTemplates({
      status: str(params.status) || "all",
      keyword: str(params.keyword),
      tags: strList(params.tags),
      page: num(params.page),
      pageSize: num(params.pageSize),
    }),
  "clientTemplate.detail": (params: Record<string, unknown>) =>
    getClientTemplateByCode(str(params.code)),
  "clientTemplate.createdClients": (params: Record<string, unknown>) =>
    clientsCreatedByTemplate(str(params.code)),

  "callLog.list": (params: Record<string, unknown>) =>
    listCallLogs({
      start: opt(params.start),
      end: opt(params.end),
      status: str(params.status) || "all",
      model: str(params.model) || "all",
      provider: str(params.provider) || "all",
      protocol: str(params.protocol) || "all",
      routeGroup: str(params.routeGroup) || "all",
      email: str(params.email),
      appKey: str(params.appKey),
    }),
  "callLog.detail": (params: Record<string, unknown>) => getCallLogById(str(params.id)),

  "cost.list": (params: Record<string, unknown>) =>
    listCosts({
      application: str(params.application),
      appKey: str(params.appKey),
      alias: str(params.alias),
      model: str(params.model) || "all",
      provider: str(params.provider) || "all",
      start: opt(params.start),
      end: opt(params.end),
    }),
  "cost.detail": (params: Record<string, unknown>) => getCostById(str(params.id)),

  "audit.list": (params: Record<string, unknown>) =>
    listAudits({
      start: opt(params.start),
      end: opt(params.end),
      operator: str(params.operator) || "all",
      operation: str(params.operation) || "all",
      resourceType: str(params.resourceType) || "all",
      resourceId: str(params.resourceId),
    }),
  "audit.detail": (params: Record<string, unknown>) => getAuditById(str(params.id)),
  "audit.operators": () => listAuditOperators(),

  "dashboard.query": (params: Record<string, unknown>) =>
    queryDashboardReal(opt(params.start), opt(params.end)),
  "ops.modelUsage": (params: Record<string, unknown>) =>
    queryModelUsageReal(opt(params.start), opt(params.end)),
  "ops.providerUsage": (params: Record<string, unknown>) =>
    queryProviderUsageReal(opt(params.start), opt(params.end)),
  "ops.clientUsage": (params: Record<string, unknown>) =>
    queryClientUsageReal(opt(params.start), opt(params.end)),
  "ops.availability": (params: Record<string, unknown>) =>
    queryAvailabilityReal(opt(params.start), opt(params.end)),
} as const;

export interface MutationContext {
  /** 路径段中的业务键（code） */
  id?: string;
  body: unknown;
}

/** 写服务："METHOD 资源路径" → 执行函数（id 为路径中的 code） */
export const pactorMutations: Record<
  string,
  (ctx: MutationContext) => Promise<unknown>
> = {
  "POST providers": (ctx) =>
    createProvider((ctx.body ?? {}) as ProviderCreateRequest & Record<string, unknown>),
  // 字段拆分（设计 D2）：契约字段 → 后端 PUT；status 变化 → enable/disable；
  // 契约外字段（description/proxy/capabilityExtension/apiKey）落桩忽略
  "PUT providers": async (ctx) => {
    const body = (ctx.body ?? {}) as Record<string, unknown>;
    const code = str(ctx.id);
    const patch: ProviderUpdateRequest & Record<string, unknown> = {};
    if (typeof body.displayName === "string" && body.displayName !== "") {
      patch.displayName = body.displayName;
    }
    if (typeof body.baseUrl === "string" && body.baseUrl !== "") patch.baseUrl = body.baseUrl;
    if (typeof body.region === "string") patch.region = body.region;
    const timeoutMs = Number(body.timeoutMs);
    if (Number.isFinite(timeoutMs) && timeoutMs > 0) patch.timeoutMs = timeoutMs;
    const updated = await updateProvider(code, patch);
    if (typeof body.status === "string" && body.status !== "") {
      const targetEnabled = body.status === "enabled";
      const currentEnabled = updated.status === "ENABLED";
      if (targetEnabled !== currentEnabled) {
        await setProviderEnabled(code, targetEnabled);
      }
    }
    return updated;
  },
  "POST providers/enable": (ctx) => setProviderEnabled(str(ctx.id), true),
  "POST providers/disable": (ctx) => setProviderEnabled(str(ctx.id), false),
  "POST providers/connection-test": (ctx) => testProviderConnection(str(ctx.id)),

  "POST models": (ctx) => createModel((ctx.body ?? {}) as Record<string, unknown>),
  // 字段拆分（同 provider）：契约字段 → updateModel；status 变化 → enable/disable；其余落桩
  "PUT models": async (ctx) => {
    const body = (ctx.body ?? {}) as Record<string, unknown>;
    const code = str(ctx.id);
    const updated = await updateModel(code, body);
    if (typeof body.status === "string" && body.status !== "") {
      const targetEnabled = body.status === "enabled";
      const currentEnabled = updated.status === "ENABLED";
      if (targetEnabled !== currentEnabled) {
        await setModelEnabled(code, targetEnabled);
      }
    }
    return updated;
  },
  "POST models/enable": (ctx) => setModelEnabled(str(ctx.id), true),
  "POST models/disable": (ctx) => setModelEnabled(str(ctx.id), false),
  "POST models/invoke-test": (ctx) => invokeModelTest(str(ctx.id)),

  "POST routes": (ctx) => createRoute((ctx.body ?? {}) as Record<string, unknown>),
  "PUT routes": (ctx) => updateRoute(str(ctx.id), (ctx.body ?? {}) as Record<string, unknown>),
  "POST routes/publish": (ctx) =>
    publishRoute(str(ctx.id), (ctx.body ?? {}) as { changeSummary?: string; operatorId?: string }),
  "POST routes/enable": (ctx) => setRouteEnabled(str(ctx.id), true),
  "POST routes/disable": (ctx) => setRouteEnabled(str(ctx.id), false),

  "POST clients": (ctx) => createClient((ctx.body ?? {}) as Record<string, unknown>),
  "PUT clients": (ctx) => updateClient(str(ctx.id), (ctx.body ?? {}) as Record<string, unknown>),
  "POST clients/enable": (ctx) => setClientEnabled(str(ctx.id), true),
  "POST clients/disable": (ctx) => setClientEnabled(str(ctx.id), false),
  "POST clients/rotate-app-key": (ctx) => rotateClientAppKey(str(ctx.id)),
  "PUT clients/route-grants": (ctx) =>
    updateClientRouteGrants(str(ctx.id), ((ctx.body ?? {}) as { routeIds?: string[] }).routeIds ?? []),

  "POST client-templates": (ctx) =>
    createClientTemplate((ctx.body ?? {}) as Record<string, unknown>),
  "PUT client-templates": (ctx) =>
    updateClientTemplate(str(ctx.id), (ctx.body ?? {}) as Record<string, unknown>),
  "POST client-templates/enable": (ctx) => setClientTemplateEnabled(str(ctx.id), true),
  "POST client-templates/disable": (ctx) => setClientTemplateEnabled(str(ctx.id), false),

  "POST debug/model-invoke": (ctx) => {
    const body = (ctx.body ?? {}) as {
      modelCode: string;
      systemPrompt?: string;
      text: string;
      chatParams?: Record<string, unknown>;
    };
    return invokeModelDebug({
      modelCode: str(body.modelCode),
      systemPrompt: body.systemPrompt,
      text: str(body.text),
      chatParams: body.chatParams,
    });
  },
  "POST debug/client-invoke": (ctx) => {
    const body = (ctx.body ?? {}) as {
      clientCode: string;
      routeCode: string;
      text: string;
      routeTag?: string;
    };
    return invokeClientDebug({
      clientCode: str(body.clientCode),
      routeCode: str(body.routeCode),
      text: str(body.text),
      routeTag: body.routeTag,
    });
  },
};

/**
 * 无后端接口的写操作桩（设计 D4）：集中登记，默认返回成功并标注 stub: true，
 * 不触碰真实后端（mock 演示模式下同样可用）。后端接口就绪后逐条移除、
 * 改挂 pactorMutations 中的真实适配器调用。
 */
export const stubMutations: Record<
  string,
  (ctx: MutationContext) => Promise<unknown>
> = {
  // 后端无 DELETE /providers/{id}（OpenAPI 已核实）
  "DELETE providers": async () => ({ stub: true, ok: true }),
};
