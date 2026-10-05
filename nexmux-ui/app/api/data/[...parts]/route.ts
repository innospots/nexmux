import { cookies } from "next/headers";

import { findUserBySession, SESSION_COOKIE } from "@/server/accounts";
import { corsHeaders, corsPreflight } from "@/server/cors";
import { pactorServices } from "@/pactor/services";
import { backendConfig } from "@/pactor/backend/config";
import { BackendError } from "@/pactor/backend/client";
import { pactorMutations } from "@/pactor/backend/services-real";

/**
 * 页面数据接口（mock）：页面 DSL 的 http 数据源统一入口。
 * URL 路径段 → 服务名映射（scripts/to-http-dsl.mjs 中的同一张表）；
 * 接入真实后端时改写本表（或在 src/pactor/config.ts 调整 baseUrl 直连后端）。
 *
 * 查询参数原为 service 包装层的入参（布尔快筛在 DSL 侧是一族一个参数），
 * 经 HTTP 序列化为字符串，此处把 'true'/'false' 还原为布尔后再复用
 * 与客户端注册表同一套包装层（src/pactor/services.ts）。
 * 接口侧鉴权：匿名一律 401。
 */

/** 路径段 → 服务名 */
const ROUTE_TABLE: Record<string, string> = {
  dashboard: "dashboard.query",
  providers: "provider.list",
  models: "model.list",
  routes: "route.list",
  clients: "client.list",
  "client-templates": "clientTemplate.list",
  "call-logs": "callLog.list",
  costs: "cost.list",
  audit: "audit.list",
  "audit-operators": "audit.operators",
  "ops/model-usage": "ops.modelUsage",
  "ops/provider-usage": "ops.providerUsage",
  "ops/client-usage": "ops.clientUsage",
  "ops/provider-availability": "ops.availability",
};

/** 详情 / 子资源：<res>/<code|id> 或 <res>/<code>/<sub> */
const DETAIL_TABLE: Record<string, string> = {
  providers: "provider.detail",
  models: "model.detail",
  routes: "route.detail",
  clients: "client.detail",
  "client-templates": "clientTemplate.detail",
  "call-logs": "callLog.detail",
  costs: "cost.detail",
  audit: "audit.detail",
};

const SUB_TABLE: Record<string, string> = {
  "providers/pull-models": "provider.pullModels",
  "client-templates/created-clients": "clientTemplate.createdClients",
};

/** 详情类服务的标识参数名（code / id） */
const ID_PARAM: Record<string, string> = {
  "callLog.detail": "id",
  "cost.detail": "id",
  "audit.detail": "id",
};

function resolveService(parts: string[]): { service: string; id?: string } | null {
  if (parts.length === 0 || parts.length > 3) return null;
  if (parts.length === 1 || parts.length === 2 && parts[0] === "ops") {
    const service = ROUTE_TABLE[parts.join("/")];
    return service ? { service } : null;
  }
  const [resource, id, sub] = parts;
  if (parts.length === 3) {
    const service = SUB_TABLE[`${resource}/${sub}`];
    return service ? { service, id } : null;
  }
  const service = DETAIL_TABLE[resource];
  return service ? { service, id } : null;
}

/** query string → 服务入参：'true'/'false' 还原为布尔，其余保持字符串 */
function coerceParams(searchParams: URLSearchParams): Record<string, unknown> {
  const params: Record<string, unknown> = {};
  for (const [key, value] of searchParams.entries()) {
    params[key] = value === "true" ? true : value === "false" ? false : value;
  }
  return params;
}

export async function GET(
  request: Request,
  context: RouteContext<"/api/data/[...parts]">,
) {
  const store = await cookies();
  const user = findUserBySession(store.get(SESSION_COOKIE)?.value);
  if (!user) {
    return Response.json(
      { error: "未登录" },
      { status: 401, headers: corsHeaders(request) },
    );
  }

  const { parts } = await context.params;
  const resolved = resolveService(parts);
  if (!resolved) {
    return Response.json(
      { error: `未知数据接口: /api/data/${parts.join("/")}` },
      { status: 404, headers: corsHeaders(request) },
    );
  }

  const params = coerceParams(new URL(request.url).searchParams);
  if (resolved.id !== undefined) {
    params[ID_PARAM[resolved.service] ?? "code"] = resolved.id;
  }

  try {
    const result = await pactorServices[resolved.service](params);
    return Response.json(result ?? null, {
      headers: { "cache-control": "no-store", ...corsHeaders(request) },
    });
  } catch (error) {
    return mutationErrorResponse(request, error);
  }
}

/** 写通道（设计 §7.2）：POST/PUT /api/data/<res>[/<code>[/<sub>]] → pactorMutations */
async function handleMutation(
  request: Request,
  context: RouteContext<"/api/data/[...parts]">,
) {
  const store = await cookies();
  const user = findUserBySession(store.get(SESSION_COOKIE)?.value);
  if (!user) {
    return Response.json(
      { error: "未登录" },
      { status: 401, headers: corsHeaders(request) },
    );
  }
  if (!backendConfig().enabled) {
    return Response.json(
      { error: "当前为演示模式（mock），写操作不可用" },
      { status: 503, headers: corsHeaders(request) },
    );
  }

  const { parts } = await context.params;
  const [resource, id, sub] = parts;
  // 候选 key：3 段 = METHOD resource/sub；2 段先试 METHOD resource/{id字面}（子操作）
  // 再回退 METHOD resource；1 段 = METHOD resource
  const candidates = sub
    ? [`${request.method} ${resource}/${sub}`]
    : id
      ? [`${request.method} ${resource}/${id}`, `${request.method} ${resource}`]
      : [`${request.method} ${resource}`];
  const key = candidates.find((candidate) => pactorMutations[candidate]);
  const handler = key ? pactorMutations[key] : undefined;
  if (!handler) {
    return Response.json(
      { error: `未知写接口: ${request.method} /api/data/${parts.join("/")}` },
      { status: 404, headers: corsHeaders(request) },
    );
  }

  try {
    const body = await request.json().catch(() => ({}));
    const result = await handler({ id, body });
    return Response.json(result ?? null, {
      headers: { "cache-control": "no-store", ...corsHeaders(request) },
    });
  } catch (error) {
    return mutationErrorResponse(request, error);
  }
}

/** 后端错误 → 页面 message 语义（设计 §8 错误映射） */
function mutationErrorResponse(request: Request, error: unknown) {
  if (error instanceof BackendError) {
    return Response.json(
      { error: error.message, code: error.code },
      { status: error.httpStatus, headers: corsHeaders(request) },
    );
  }
  return Response.json(
    { error: error instanceof Error ? error.message : "服务内部错误" },
    { status: 500, headers: corsHeaders(request) },
  );
}

export const POST = handleMutation;
export const PUT = handleMutation;

/** 预检（跨源带凭据 / 自定义头时） */
export const OPTIONS = corsPreflight;
