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
  type ProviderWriteBody,
} from "./adapters/provider-adapter";
import {
  createModel,
  getModelByCode,
  listModels,
  setModelEnabled,
  updateModel,
} from "./adapters/model-adapter";

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

/** 读服务（与 mock handler 同签名） */
export const realServices = {
  "provider.list": (params: Record<string, unknown>) =>
    listProviders({
      keyword: str(params.keyword),
      status: str(params.status) || "all",
      types: strList(params.types),
    }),
  "provider.detail": (params: Record<string, unknown>) => getProviderByCode(str(params.code)),
  "provider.pullModels": (params: Record<string, unknown>) => pullProviderModels(str(params.code)),

  "model.list": (params: Record<string, unknown>) =>
    listModels({
      type: str(params.type) || "all",
      providers: strList(params.providers),
    }),
  "model.detail": (params: Record<string, unknown>) => getModelByCode(str(params.code)),
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
  "POST providers": (ctx) => createProvider((ctx.body ?? {}) as ProviderWriteBody),
  "PUT providers": (ctx) =>
    updateProvider(str(ctx.id), (ctx.body ?? {}) as ProviderWriteBody),
  "POST providers/enable": (ctx) => setProviderEnabled(str(ctx.id), true),
  "POST providers/disable": (ctx) => setProviderEnabled(str(ctx.id), false),
  "POST providers/connection-test": (ctx) => testProviderConnection(str(ctx.id)),

  "POST models": (ctx) => createModel((ctx.body ?? {}) as Record<string, unknown>),
  "PUT models": (ctx) => updateModel(str(ctx.id), (ctx.body ?? {}) as Record<string, unknown>),
  "POST models/enable": (ctx) => setModelEnabled(str(ctx.id), true),
  "POST models/disable": (ctx) => setModelEnabled(str(ctx.id), false),
};
