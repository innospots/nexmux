/**
 * 调试页浏览器侧数据源：全部经 BFF（/api/data/*）取真实后端数据。
 * 与页面 actions（src/pactor/actions.ts）配套；错误以异常上抛，
 * 由 action handler 转为页面错误消息展示。
 */

import type { ClientDebugRound } from "./backend/contracts";
import type {
  AuthRoute,
  ConnectionTestResult,
  ModelDebugParams,
  ModelDebugResult,
  ModelParams,
  ProviderModel,
} from "./backend/contracts";

// ── http 基础 ──────────────────────────────────────────────────

async function callJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { "content-type": "application/json" },
    ...init,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

// ── 选项数据 ───────────────────────────────────────────────────

export async function modelOptionsOf(
  providerCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ models: ProviderModel[] }>(
    `/api/data/providers/${encodeURIComponent(providerCode)}`,
  );
  return detail.models
    .filter((m) => m.status === "ONLINE")
    .map((m) => ({ value: m.code, label: `${m.name}（${m.providerModelId}）` }));
}

interface ModelDetailForDebug {
  params: ModelParams;
}

export async function capabilitiesOf(modelCode: string): Promise<string[]> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/data/models/${encodeURIComponent(modelCode)}`,
  );
  return detail.params.capabilities;
}

export async function supportsReasoning(modelCode: string): Promise<boolean> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/data/models/${encodeURIComponent(modelCode)}`,
  );
  const effort = detail.params.reasoningEffort;
  return !!effort && effort !== "—";
}

export async function paramDefaultsOf(modelCode: string): Promise<{
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  reasoningEffort?: string;
}> {
  const detail = await callJson<ModelDetailForDebug>(
    `/api/data/models/${encodeURIComponent(modelCode)}`,
  );
  const num = (v: string) => (v === "—" || v === "" ? undefined : Number(v));
  return {
    temperature: num(detail.params.temperature),
    topP: num(detail.params.topP),
    maxTokens: num(detail.params.maxTokens),
    reasoningEffort:
      detail.params.reasoningEffort !== "—" ? detail.params.reasoningEffort : undefined,
  };
}

export async function appKeyOptionsOf(
  clientCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ appKeyId: string }>(
    `/api/data/clients/${encodeURIComponent(clientCode)}`,
  );
  return [{ value: detail.appKeyId, label: detail.appKeyId }];
}

export async function routeOptionsOf(
  clientCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ authRoutes: AuthRoute[] }>(
    `/api/data/clients/${encodeURIComponent(clientCode)}`,
  );
  return detail.authRoutes.map((r) => ({ value: r.code, label: `${r.name}（${r.alias}）` }));
}

export async function routeTagOptionsOf(
  routeCode: string,
): Promise<{ value: string; label: string }[]> {
  const detail = await callJson<{ tags: string[] }>(
    `/api/data/routes/${encodeURIComponent(routeCode)}`,
  );
  return detail.tags.map((t) => ({ value: t, label: t }));
}

// ── 写动作（真实调用） ─────────────────────────────────────────

export async function testConnectionReal(providerCode: string): Promise<ConnectionTestResult> {
  return callJson<ConnectionTestResult>(
    `/api/data/providers/${encodeURIComponent(providerCode)}/connection-test`,
    { method: "POST" },
  );
}

export async function simulateModelCallReal(
  _providerCode: string,
  modelCode: string,
  systemPrompt: string,
  params: ModelDebugParams,
  userText: string,
): Promise<ModelDebugResult> {
  return callJson<ModelDebugResult>(`/api/data/debug/model-invoke`, {
    method: "POST",
    body: JSON.stringify({
      modelCode,
      systemPrompt,
      text: userText,
      chatParams: {
        temperature: params.temperature,
        topP: params.topP,
        maxTokens: params.maxTokens,
        reasoningEffort: params.reasoningEffort,
      },
    }),
  });
}

export async function simulateClientCallReal(
  context: {
    clientCode: string;
    appKeyId: string;
    routeCode: string;
    channel: string;
    routeTag: string;
    user: string;
  },
  userText: string,
  seq: number,
): Promise<ClientDebugRound> {
  const result = await callJson<Omit<ClientDebugRound, "id" | "seq">>(
    `/api/data/debug/client-invoke`,
    {
      method: "POST",
      body: JSON.stringify({
        clientCode: context.clientCode,
        routeCode: context.routeCode,
        text: userText,
        routeTag: context.routeTag,
      }),
    },
  );
  return { id: `round-${seq}`, seq, ...result };
}
