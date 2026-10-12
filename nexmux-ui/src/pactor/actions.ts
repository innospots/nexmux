/**
 * 调试台的自定义动作（移植自 model-console 的 client-debug-actions.ts 与
 * model-debug-actions.ts，合并为单文件），经 SitemapApp 的 `actions` prop
 * 全局注册。
 * DSL 表达式不支持数组操作 / 定时器 / 剪贴板，故对话状态变更、流式模拟、
 * 身份联动选项计算收敛在此处；handler 经 ctx.context.state 直接读写页面
 * StateStore，写入即触发 DSL 树重渲染（与内置 setState 动作同一通道）。
 *
 * ActionHandler 类型见 @pactor-app/runtime；此处按结构化子集声明参数类型
 * （handler 只需已求值的 action.params 与 state 读写），赋值给 SitemapApp
 * actions 选项时由 TS 结构化检查兼容性（1.22.0 签名：
 * (action: ActionConfig, ctx: ActionContext) => ActionResult | Promise<…>）。
 */

import type { ActionHandler } from "@pactor-app/runtime";

import {
  appKeyOptionsOf,
  capabilitiesOf,
  modelOptionsOf,
  paramDefaultsOf,
  routeOptionsOf,
  routeTagOptionsOf,
  simulateModelCallReal,
  streamClientCall,
  streamModelCall,
  supportsReasoning,
  testConnectionReal,
} from "./debug-data";
import { callJson } from "./debug-data";
import type {
  ClientDebugRound,
  DebugStreamError,
  DebugStreamMeta,
} from "./debug-data";
import { type DebugChatMessage } from "./components/debug-chat";

/** SitemapApp actions 选项 handler 的结构化最小契约（ActionHandler 子集） */
export interface DebugActionContext {
  context: {
    state: {
      get: (path: string) => unknown;
      set: (patch: Record<string, unknown>) => void;
    };
  };
}

export type DebugAction = (
  action: { params?: Record<string, unknown> },
  ctx: DebugActionContext,
) => { ok: boolean; data?: unknown; error?: Error } | Promise<{ ok: boolean; data?: unknown; error?: Error }>;

function str(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function num(value: unknown): number | undefined {
  return typeof value === "number" ? value : undefined;
}

export function createClientDebugActions(): Record<string, DebugAction> {
  /** 流式定时器的停止闭包（停止生成 / 清空对话 / 下一次发送前取消） */
  let stopCurrent: (() => void) | null = null;

  /** 流式收尾：assistant 消息落定 + 轮次写入 selectedRound（右侧返回报文此时可见） */
  const finalize = (
    ctx: DebugActionContext,
    round: ClientDebugRound,
    assistantId: string,
    finalText: string,
    stopped: boolean,
  ) => {
    const messages = (
      (ctx.context.state.get("messages") as DebugChatMessage[]) ?? []
    ).map((m) =>
      m.id === assistantId
        ? {
            ...m,
            content: finalText,
            streaming: false,
            meta: `Token ${round.tokensIn}/${round.tokensOut} · TTFT ${round.ttftMs}ms · 延迟 ${round.latencyMs}ms${stopped ? " · 已停止" : ""}`,
          }
        : m,
    );
    ctx.context.state.set({
      messages,
      selectedRound: round,
      selectedRoundId: round.id,
      streaming: false,
    });
    stopCurrent = null;
  };

  return {
    /** 切换客户端：联动 AppKey（取该客户端唯一一把）、授权路由与上下文 */
    "clientDebug.selectClient": async (action, ctx) => {
      const clientCode = str(action.params?.clientCode);
      const [appKeys, routes] = await Promise.all([
        appKeyOptionsOf(clientCode),
        routeOptionsOf(clientCode),
      ]);
      ctx.context.state.set({
        clientCode,
        appKeyOptions: appKeys,
        appKeyId: appKeys[0]?.value ?? "",
        routeOptions: routes,
        routeCode: "",
        tagOptions: [],
        routeTag: "",
      });
      return { ok: true, data: true };
    },

    /** 切换授权路由：联动 RouteTag 选项 */
    "clientDebug.selectRoute": async (action, ctx) => {
      const routeCode = str(action.params?.routeCode);
      ctx.context.state.set({
        routeCode,
        tagOptions: await routeTagOptionsOf(routeCode),
        routeTag: "",
      });
      return { ok: true, data: true };
    },

    /** 发送：真实全链路 SSE 流式调用（服务端穿透上游，逐 chunk 渲染），停止 = AbortController */
    "clientDebug.send": async (action, ctx) => {
      const state = ctx.context.state;
      if (state.get("streaming")) return { ok: false };
      const clientCode = str(state.get("clientCode"));
      const appKeyId = str(state.get("appKeyId"));
      const routeCode = str(state.get("routeCode"));
      const text = str(action.params?.text);
      if (!clientCode || !appKeyId || !routeCode || !text.trim()) {
        return { ok: false };
      }

      const roundCount = Number(state.get("roundCount") ?? 0);
      const seq = roundCount + 1;
      const roundId = `round-${seq}`;
      const assistantId = `${roundId}-a`;
      state.set({
        messages: [
          ...((state.get("messages") as DebugChatMessage[]) ?? []),
          { id: `${roundId}-u`, role: "user", content: text },
          { id: assistantId, role: "assistant", content: "", streaming: true },
        ],
        streaming: true,
        roundCount: seq,
        // 流结束后再写入 selectedRound（PRD：返回报文在流结束后刷新）
        selectedRound: null,
        selectedRoundId: roundId,
        tab: "request",
      });

      const controller = new AbortController();
      stopCurrent = () => controller.abort();
      let acc = "";
      const updateAssistant = () => {
        const cur =
          (ctx.context.state.get("messages") as DebugChatMessage[]) ?? [];
        ctx.context.state.set({
          messages: cur.map((m) =>
            m.id === assistantId ? { ...m, content: acc } : m,
          ),
        });
      };
      /** 报文面板形状：pretty / 压缩双形态（原始文本非 JSON 时原样保留） */
      const pretty = (raw?: string) => {
        if (!raw) return "";
        try {
          return JSON.stringify(JSON.parse(raw), null, 2);
        } catch {
          return raw;
        }
      };
      const compact = (raw?: string) => {
        if (!raw) return "";
        try {
          return JSON.stringify(JSON.parse(raw));
        } catch {
          return raw;
        }
      };
      /** 轮次数据：成功取 debug-meta，失败取 debug-error（responseJson 为错误对象） */
      const roundOf = (
        meta: DebugStreamMeta | null,
        error: DebugStreamError | null,
      ): ClientDebugRound => ({
        id: roundId,
        seq,
        requestJson: pretty(meta?.requestJson),
        requestCompact: compact(meta?.requestJson),
        responseJson: error
          ? JSON.stringify({ error: error.message }, null, 2)
          : pretty(meta?.responseJson),
        responseCompact: error
          ? JSON.stringify({ error: error.message })
          : compact(meta?.responseJson),
        status: error ? error.status : (meta?.status ?? 0),
        latencyMs: meta?.latencyMs ?? 0,
        ttftMs: meta?.ttftMs ?? 0,
        routeGroup: meta?.routeGroup ?? "default",
        target: meta?.target ?? "",
        tokensIn: meta?.tokensIn ?? 0,
        tokensOut: meta?.tokensOut ?? 0,
        quotaPreview: meta?.quotaPreview ?? "",
        rateLimitResult: meta?.rateLimitResult ?? "",
        requestId: meta?.requestId ?? error?.requestId ?? "",
        reply: acc,
      });
      /** 轮次落定：finalize 后把 round 挂到消息上（点击消息联动右侧报文面板） */
      const settle = (
        meta: DebugStreamMeta | null,
        error: DebugStreamError | null,
        finalText: string,
        stopped: boolean,
      ) => {
        const round = roundOf(meta, error);
        finalize(ctx, round, assistantId, finalText, stopped);
        const cur =
          (ctx.context.state.get("messages") as DebugChatMessage[]) ?? [];
        ctx.context.state.set({
          messages: cur.map((m) =>
            m.id === assistantId || m.id === `${roundId}-u`
              ? { ...m, round }
              : m,
          ),
        });
      };
      try {
        await streamClientCall(
          { clientCode, routeCode, routeTag: str(state.get("routeTag")) },
          text,
          {
            onDelta: (delta) => {
              acc += delta;
              updateAssistant();
            },
            onMeta: (meta) => settle(meta, null, acc, false),
            onError: (error) =>
              settle(
                null,
                error,
                `【调用失败】HTTP ${error.status}：${error.message}`,
                false,
              ),
          },
          controller.signal,
        );
      } catch (error) {
        if (controller.signal.aborted) {
          settle(null, null, acc, true);
        } else {
          settle(
            null,
            {
              status: 502,
              message: error instanceof Error ? error.message : "网络错误",
            },
            acc || "【调用失败】网络错误",
            false,
          );
        }
      }
      return { ok: true, data: true };
    },

    /** 停止生成：取消定时器并按当前片段收尾 */
    "clientDebug.stop": (_action, ctx) => {
      stopCurrent?.();
      if (ctx.context.state.get("streaming")) {
        ctx.context.state.set({ streaming: false });
      }
      return { ok: true, data: true };
    },

    /** 清空对话：清空本地会话（不影响已落库调用日志） */
    "clientDebug.clear": (_action, ctx) => {
      stopCurrent?.();
      stopCurrent = null;
      ctx.context.state.set({
        messages: [],
        selectedRound: null,
        selectedRoundId: "",
        roundCount: 0,
        streaming: false,
      });
      return { ok: true, data: true };
    },

    /** 复制报文到剪贴板（DSL 无剪贴板能力，经动作桥接） */
    "clientDebug.copy": (action) => {
      const text = action.params?.text;
      if (typeof text === "string" && text) {
        void navigator.clipboard?.writeText(text);
      }
      return { ok: true, data: true };
    },
  };
}

export function createModelDebugActions(): Record<string, DebugAction> {
  let stopCurrent: (() => void) | null = null;
  let seq = 0;

  /** assistant 消息收尾（流式完成 / 停止生成共用） */
  const finalize = (
    ctx: DebugActionContext,
    id: string,
    patch: Partial<DebugChatMessage>,
  ) => {
    const messages = (
      (ctx.context.state.get("messages") as DebugChatMessage[]) ?? []
    ).map((m) => (m.id === id ? { ...m, ...patch } : m));
    ctx.context.state.set({ messages, streaming: false });
    stopCurrent = null;
  };

  return {
    /** 切换供应商：联动刷新模型列表并清空已选模型与连接状态 */
    "modelDebug.selectProvider": async (action, ctx) => {
      const providerCode = str(action.params?.providerCode);
      ctx.context.state.set({
        providerCode,
        modelOptions: await modelOptionsOf(providerCode),
        modelCode: "",
        conn: null,
        showReasoning: false,
        canStream: true,
        canTools: false,
        temperature: undefined,
        topP: undefined,
        maxTokens: undefined,
        streamingOn: true,
        toolsOn: false,
      });
      return { ok: true, data: true };
    },

    /** 切换模型：回填默认参数 + 按 Capability 计算参数显隐/开关可用性 */
    "modelDebug.selectModel": async (action, ctx) => {
      const modelCode = str(action.params?.modelCode);
      const [caps, defaults, reasoning] = await Promise.all([
        capabilitiesOf(modelCode),
        paramDefaultsOf(modelCode),
        supportsReasoning(modelCode),
      ]);
      // catalog 能力词表为小写标签（reasoning / vision / tools …），无流式标记：
      // OpenAI 兼容上游默认支持流式，故 Streaming 恒可开、默认开
      const capsLower = caps.map((c) => c.toLowerCase());
      ctx.context.state.set({
        modelCode,
        conn: null,
        temperature: defaults.temperature,
        topP: defaults.topP,
        maxTokens: defaults.maxTokens,
        reasoningEffort: defaults.reasoningEffort ?? "medium",
        showReasoning: reasoning,
        canStream: true,
        canTools: capsLower.some((c) =>
          ["tools", "tool-calling", "function-calling"].includes(c),
        ),
        streamingOn: true,
        toolsOn: false,
      });
      return { ok: true, data: true };
    },

    /** 连接测试：不发对话，仅探测凭证与 Endpoint 可达性（真实 BFF；演示模式回退模拟） */
    "modelDebug.testConnection": async (_action, ctx) => {
      const state = ctx.context.state;
      const providerCode = str(state.get("providerCode"));
      if (!providerCode || !str(state.get("modelCode")) || state.get("testing")) {
        return { ok: false };
      }
      state.set({ testing: true, conn: null });
      try {
        const conn = await testConnectionReal(providerCode);
        ctx.context.state.set({ conn, testing: false });
      } catch (error) {
        ctx.context.state.set({
          conn: {
            ok: false,
            latencyMs: 0,
            detail: error instanceof Error ? error.message : "连接测试失败",
          },
          testing: false,
        });
      }
      return { ok: true, data: true };
    },

    /** 发送：直连供应商模型（真实 BFF 穿透；演示模式回退本地模拟） */
    "modelDebug.send": async (action, ctx) => {
      const state = ctx.context.state;
      if (state.get("streaming")) return { ok: false };
      const providerCode = str(state.get("providerCode"));
      const modelCode = str(state.get("modelCode"));
      const text = str(action.params?.text);
      if (!providerCode || !modelCode || !text.trim()) return { ok: false };

      seq += 1;
      const msgId = `m-${seq}`;
      const messages = (state.get("messages") as DebugChatMessage[]) ?? [];
      const userMsg: DebugChatMessage = {
        id: `${msgId}-u`,
        role: "user",
        content: text,
      };

      const streamingOn =
        state.get("streamingOn") === true && state.get("canStream") !== false;
      const chatParams = {
        temperature: num(state.get("temperature")),
        topP: num(state.get("topP")),
        maxTokens: num(state.get("maxTokens")),
        reasoningEffort:
          state.get("showReasoning") === true
            ? str(state.get("reasoningEffort"))
            : undefined,
      };

      // 流式：SSE 直连上游，逐 chunk 渲染；停止生成 = AbortController 中断
      if (streamingOn) {
        state.set({
          messages: [
            ...messages,
            userMsg,
            { id: msgId, role: "assistant", content: "", streaming: true },
          ],
          streaming: true,
        });
        const controller = new AbortController();
        stopCurrent = () => controller.abort();
        let acc = "";
        const updateAssistant = () => {
          const cur =
            (ctx.context.state.get("messages") as DebugChatMessage[]) ?? [];
          ctx.context.state.set({
            messages: cur.map((m) =>
              m.id === msgId ? { ...m, content: acc } : m,
            ),
          });
        };
        /** 失败：占位 assistant 消息替换为错误消息（PRD 交互规则 3） */
        const fail = (message: string) => {
          const cur =
            (ctx.context.state.get("messages") as DebugChatMessage[]) ?? [];
          ctx.context.state.set({
            messages: cur.map((m) =>
              m.id === msgId
                ? { id: `${msgId}-e`, role: "error", content: message }
                : m,
            ),
            streaming: false,
          });
          stopCurrent = null;
        };
        try {
          await streamModelCall(
            modelCode,
            str(state.get("systemPrompt")),
            chatParams,
            text,
            {
              onDelta: (delta) => {
                acc += delta;
                updateAssistant();
              },
              onMeta: (meta) =>
                finalize(ctx, msgId, {
                  streaming: false,
                  meta: `Token ${meta.tokensIn ?? "—"}/${meta.tokensOut ?? "—"} · TTFT ${meta.ttftMs}ms · 延迟 ${meta.latencyMs}ms · 上游 ${meta.status}`,
                }),
              onError: (error) =>
                fail(`【调用失败】HTTP ${error.status}：${error.message}`),
            },
            controller.signal,
          );
        } catch (error) {
          if (controller.signal.aborted) {
            finalize(ctx, msgId, {
              content: acc,
              streaming: false,
              meta: "已停止",
            });
          } else {
            fail(
              `【调用失败】${error instanceof Error ? error.message : "网络错误"}`,
            );
          }
        }
        return { ok: true, data: true };
      }

      // 非流式：整包调用后直接落定
      const result = await simulateModelCallReal(
        providerCode,
        modelCode,
        str(state.get("systemPrompt")),
        { ...chatParams, streaming: false, tools:
            state.get("toolsOn") === true && state.get("canTools") === true },
        text,
      );
      const meta = `Token ${result.tokensIn}/${result.tokensOut} · TTFT ${result.ttftMs}ms · 延迟 ${result.latencyMs}ms · 上游 ${result.status}`;

      // 调用失败（凭证缺失 / 上游错误）：对话区以错误消息展示摘要（PRD 交互规则 3）
      if (result.status >= 400 || result.reply.startsWith("【调用失败")) {
        state.set({
          messages: [
            ...messages,
            userMsg,
            { id: `${msgId}-e`, role: "error", content: result.reply },
          ],
        });
        return { ok: true, data: true };
      }

      state.set({
        messages: [
          ...messages,
          userMsg,
          {
            id: msgId,
            role: "assistant",
            content: result.reply,
            meta,
          },
        ],
      });
      return { ok: true, data: true };
    },

    /** 停止生成 */
    "modelDebug.stop": (_action, ctx) => {
      stopCurrent?.();
      if (ctx.context.state.get("streaming")) {
        ctx.context.state.set({ streaming: false });
      }
      return { ok: true, data: true };
    },

    /** 清空对话（不影响服务端） */
    "modelDebug.clear": (_action, ctx) => {
      stopCurrent?.();
      stopCurrent = null;
      ctx.context.state.set({ messages: [], streaming: false });
      return { ok: true, data: true };
    },
  };
}

/**
 * 路由候选模型动作（route-create / route-edit 页共用）：
 * DSL 表达式不支持数组操作，候选行的增删与「表单值 + 候选数组合并提交」
 * 收敛在此。候选存 state.candidates（与 RouteCandidate 契约字段对齐：
 * modelCode / weight / priority / costRate / billingRate / status）。
 */
export interface RouteCandidateRow {
  modelCode: string;
  modelName?: string;
  providerSummary?: string;
  weight?: number;
  priority?: string;
  costRate?: number | null;
  billingRate?: number | null;
  status?: string;
}

function createRouteCandidateActions(): Record<string, DebugAction> {
  const read = (ctx: DebugActionContext): RouteCandidateRow[] =>
    (ctx.context.state.get("candidates") as RouteCandidateRow[]) ?? [];

  return {
    /** 添加 / 覆盖候选（按 modelCode 去重；params.candidate 为弹窗表单值） */
    "routeCandidates.add": (action, ctx) => {
      const c = (action.params?.candidate ?? {}) as Record<string, unknown>;
      const model = str(c.model);
      if (!model) {
        return { ok: false };
      }
      const list = read(ctx).filter((x) => x.modelCode !== model);
      list.push({
        modelCode: model,
        modelName: str(c.modelName) || model,
        weight: num(c.weight) ?? 100,
        priority: str(c.priority) || "P0",
        costRate: (c.costRate as number | null) ?? null,
        billingRate: (c.billingRate as number | null) ?? null,
        status: str(c.status) || "enabled",
      });
      ctx.context.state.set({ candidates: list });
      return { ok: true, data: true };
    },
    /** 移除候选（params.modelCode） */
    "routeCandidates.remove": (action, ctx) => {
      const modelCode = str(action.params?.modelCode);
      ctx.context.state.set({
        candidates: read(ctx).filter((x) => x.modelCode !== modelCode),
      });
      return { ok: true, data: true };
    },
    /**
     * 提交路由表单：params.values（${event} 表单值）+ state.candidates 合并为
     * body 后 POST；成功 {ok:true}，失败 {ok:false,error}（与内置 request 同形）。
     */
    "routeCandidates.submit": async (action, ctx) => {
      const p = action.params ?? {};
      const body = {
        ...((p.values as Record<string, unknown>) ?? {}),
        candidates: read(ctx),
      };
      try {
        const response = await fetch(str(p.url), {
          method: str(p.method) || "POST",
          headers: { "content-type": "application/json" },
          credentials: "include",
          body: JSON.stringify(body),
        });
        if (!response.ok) {
          const err = (await response.json().catch(() => ({}))) as {
            error?: string;
            message?: string;
          };
          return {
            ok: false,
            error: new Error(err.error ?? err.message ?? `HTTP ${response.status}`),
          };
        }
        // 命名动作结果引擎成功时只存 data；与内置 request 对齐，把响应体
        //（{ok:true}）作为 data，供 ${actions.xxx.ok} 判断
        const data = (await response.json().catch(() => ({ ok: true }))) as unknown;
        return { ok: true, data };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
  };
}


/**
 * 客户端路由授权动作（client-create / client-edit 页共用）：
 * 与 routeCandidates 同因（DSL 无数组操作）。授权行存 state.record.authRoutes
 * （{routeId, code, name, alias, typeLabel, path}，与页面 Table 列契约对齐），
 * 提交时映射为 routeIds。
 */
interface AuthRouteRow {
  routeId: string;
  code: string;
  name: string;
  alias: string;
  typeLabel?: string;
  path?: string;
}

function createClientRouteActions(): Record<string, DebugAction> {
  const readRecord = (ctx: DebugActionContext): Record<string, unknown> =>
    (ctx.context.state.get("record") as Record<string, unknown>) ?? {};
  const readRoutes = (ctx: DebugActionContext): AuthRouteRow[] =>
    (readRecord(ctx).authRoutes as AuthRouteRow[]) ?? [];
  const writeRoutes = (ctx: DebugActionContext, routes: AuthRouteRow[]) => {
    ctx.context.state.set({ record: { ...readRecord(ctx), authRoutes: routes } });
  };
  const rowId = (r: AuthRouteRow): string =>
    r.routeId ?? (r as unknown as { id: string }).id;

  return {
    /** 拉取已启用路由写入 state.routeOptions（Select {value,label} 形状）+ 行索引 */
    "clientRoutes.loadOptions": async (_action, ctx) => {
      try {
        const data = await callJson<{ items?: AuthRouteRow[] }>(
          "/api/d/routes?status=enabled&type=all&page=1&pageSize=100",
        );
        const rows = (data.items ?? []).map((r) => ({ ...r, routeId: rowId(r) }));
        ctx.context.state.set({
          routeOptionRows: rows,
          routeOptions: rows.map((r) => ({
            value: r.routeId,
            label: `${r.name}（${r.alias}）`,
          })),
        });
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
    /** 调试台客户端选项（已启用客户端 → state.clientOptions） */
    "clientDebug.loadClients": async (_action, ctx) => {
      try {
        const data = await callJson<{
          items?: { code: string; name: string }[];
        }>("/api/d/clients?status=enabled&page=1&pageSize=100");
        ctx.context.state.set({
          clientOptions: (data.items ?? []).map((c) => ({
            value: c.code,
            label: `${c.name}（${c.code}）`,
          })),
        });
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
    /** 授权一路由（params.routeId；按 routeId 去重） */
    "clientRoutes.add": (action, ctx) => {
      const routeId = str(action.params?.routeId);
      if (!routeId) {
        return { ok: false };
      }
      const rows =
        (ctx.context.state.get("routeOptionRows") as AuthRouteRow[]) ?? [];
      const found = rows.find((r) => r.routeId === routeId);
      const list = readRoutes(ctx).filter((x) => x.routeId !== routeId);
      list.push(found ?? { routeId, code: routeId, name: routeId, alias: "" });
      writeRoutes(ctx, list);
      return { ok: true, data: true };
    },
    /** 移除授权（params.routeId） */
    "clientRoutes.remove": (action, ctx) => {
      const routeId = str(action.params?.routeId);
      writeRoutes(ctx, readRoutes(ctx).filter((x) => x.routeId !== routeId));
      return { ok: true, data: true };
    },
  };
}

/**
 * 配额 / 限流策略动作（client-create / client-edit / client-detail /
 * client-template-* 页共用）：DSL 表达式不支持数组操作，规则行的增删改
 * 与「表单值 + 规则数组 + 授权路由合并提交」收敛在此。规则行存
 * state.record.quotas / state.record.rateLimits（raw 字段 + 展示标签，与
 * PolicyDerive 输出形状对齐），提交时剥回 spec 原文 JSON.stringify。
 * 展示标签与后端 PolicyDerive 口径逐字对齐（修改需同步）。
 */
interface QuotaRow {
  name: string;
  quotaType: string;
  quotaTypeLabel?: string;
  metric: string;
  metricLabel?: string;
  limit: number;
  limitText?: string;
  mode: string;
  modeLabel?: string;
  cycleUnit?: string | null;
  cycleValue?: number | null;
  cycleLabel?: string | null;
  status?: string;
}

interface RateLimitRow {
  name: string;
  limitType: string;
  limitTypeLabel?: string;
  limit: number;
  limitText?: string;
  burst?: number | null;
  scopeAll?: boolean;
  scopeRoutes?: string[];
  scopeLabel?: string;
  status?: string;
}

interface ScopeRouteRow {
  routeId: string;
  name: string;
}

const QUOTA_TYPE_LABELS: Record<string, string> = {
  lifetime: "总配额",
  cyclic: "周期循环配额",
};
const QUOTA_METRIC_LABELS: Record<string, string> = {
  REQUEST: "Request",
  INPUT_TOKEN: "Input Token",
  OUTPUT_TOKEN: "Output Token",
  TOTAL_TOKEN: "Total Token",
  COST: "Cost",
  CONCURRENT: "Concurrent",
};
const QUOTA_MODE_LABELS: Record<string, string> = {
  soft: "Soft Limit",
  hard: "Hard Limit",
};
const RATE_LIMIT_TYPE_LABELS: Record<string, string> = {
  RPM: "RPM（每分钟请求数）",
  TPM: "TPM（每分钟 Token 数）",
  CONCURRENT: "Concurrent（并发连接数）",
};

function thousands(n: number): string {
  return n.toLocaleString("en-US");
}

function cycleLabelOf(unit: string | null, value: number | null): string | null {
  if (unit == null) return null;
  if (unit === "month") return "每月（自然月）";
  if (unit === "day") return `每 ${value ?? 1} 天`;
  return `每 ${value ?? 24} 小时`;
}

/** 弹窗 cycle 选项（hour24|day7|month）→ spec cycleUnit/cycleValue。 */
function cycleSpecOf(cycle: string): [string, number] {
  if (cycle === "month") return ["month", 1];
  if (cycle === "day7") return ["day", 7];
  return ["hour", 24];
}

/** 配额规则行 → quota_spec 原文（与 PolicyDerive 解析形状对齐）。 */
function quotaSpecOf(rows: QuotaRow[]): string {
  return JSON.stringify(
    rows.map((q) => ({
      name: q.name,
      quotaType: q.quotaType,
      metric: q.metric,
      limit: q.limit,
      mode: q.mode,
      ...(q.quotaType === "cyclic"
        ? { cycleUnit: q.cycleUnit, cycleValue: q.cycleValue }
        : {}),
      status: q.status ?? "enabled",
    })),
  );
}

/** 限流规则行 → rate_limit_spec 原文（与 PolicyDerive 解析形状对齐）。 */
function rateLimitSpecOf(rows: RateLimitRow[]): string {
  return JSON.stringify(
    rows.map((r) => ({
      name: r.name,
      limitType: r.limitType,
      limit: r.limit,
      ...(r.burst != null ? { burst: r.burst } : {}),
      scopeAll: r.scopeAll !== false,
      scopeRoutes: r.scopeRoutes ?? [],
      status: r.status ?? "enabled",
    })),
  );
}

function createPolicyActions(): Record<string, DebugAction> {
  const readRecord = (ctx: DebugActionContext): Record<string, unknown> =>
    (ctx.context.state.get("record") as Record<string, unknown>) ?? {};
  const writeRecord = (ctx: DebugActionContext, patch: Record<string, unknown>) => {
    ctx.context.state.set({ record: { ...readRecord(ctx), ...patch } });
  };
  const readQuotas = (ctx: DebugActionContext): QuotaRow[] =>
    (readRecord(ctx).quotas as QuotaRow[]) ?? [];
  const readRateLimits = (ctx: DebugActionContext): RateLimitRow[] =>
    (readRecord(ctx).rateLimits as RateLimitRow[]) ?? [];
  const readPendingScope = (ctx: DebugActionContext): ScopeRouteRow[] =>
    (ctx.context.state.get("pendingScopeRoutes") as ScopeRouteRow[]) ?? [];
  /** 授权路由（scope 候选来源；AuthRoute 含 routeId/name） */
  const authRoutes = (ctx: DebugActionContext): ScopeRouteRow[] =>
    ((readRecord(ctx).authRoutes as ScopeRouteRow[]) ?? []).map((r) => ({
      routeId: r.routeId,
      name: r.name,
    }));

  return {
    /** 打开限流弹窗前：重置指定路由暂存与 scope 单选（新增） */
    "policy.beginRateLimitAdd": (_action, ctx) => {
      ctx.context.state.set({ pendingScopeRoutes: [], rateLimitScope: "all" });
      return { ok: true, data: true };
    },
    /** 打开限流弹窗前：按行 scopeRoutes（路由 ID）回填暂存与 scope 单选（编辑） */
    "policy.beginRateLimitEdit": (action, ctx) => {
      const ids = (action.params?.scopeRoutes as string[]) ?? [];
      const known = authRoutes(ctx);
      ctx.context.state.set({
        pendingScopeRoutes: ids.map((id) => ({
          routeId: id,
          name: known.find((r) => r.routeId === id)?.name ?? id,
        })),
        rateLimitScope: action.params?.scopeAll === false ? "routes" : "all",
      });
      return { ok: true, data: true };
    },
    /** 作用范围候选：当前授权路由 → Select {value,label} */
    "policy.scopeRouteOptions": (_action, ctx) => {
      ctx.context.state.set({
        scopeRouteOptions: authRoutes(ctx).map((r) => ({
          value: r.routeId,
          label: r.name,
        })),
      });
      return { ok: true, data: true };
    },
    /** 暂存区加一条指定路由（params.routeId；去重） */
    "policy.addScopeRoute": (action, ctx) => {
      const routeId = str(action.params?.routeId);
      if (!routeId) return { ok: false };
      const found = authRoutes(ctx).find((r) => r.routeId === routeId);
      const list = readPendingScope(ctx).filter((r) => r.routeId !== routeId);
      list.push({ routeId, name: found?.name ?? routeId });
      ctx.context.state.set({ pendingScopeRoutes: list });
      return { ok: true, data: true };
    },
    /** 暂存区移除一条指定路由（params.routeId） */
    "policy.removeScopeRoute": (action, ctx) => {
      const routeId = str(action.params?.routeId);
      ctx.context.state.set({
        pendingScopeRoutes: readPendingScope(ctx).filter((r) => r.routeId !== routeId),
      });
      return { ok: true, data: true };
    },
    /**
     * 保存配额规则（按 name upsert；params.original 为编辑前名称，改名时
     * 连同旧名一并移除；params.quota 为弹窗表单值）。
     */
    "policy.upsertQuota": (action, ctx) => {
      const p = action.params ?? {};
      const v = (p.quota ?? {}) as Record<string, unknown>;
      const name = str(v.name);
      if (!name) return { ok: false };
      const quotaType = str(v.quotaType) || "lifetime";
      const metric = str(v.metric) || "TOTAL_TOKEN";
      const mode = str(v.mode) || "soft";
      const limit = num(v.limit) ?? 0;
      const cyclic = quotaType === "cyclic";
      const [cycleUnit, cycleValue] = cyclic
        ? cycleSpecOf(str(v.cycle))
        : [null, null];
      const original = str(p.original);
      const prev = readQuotas(ctx).find(
        (x) => x.name === (original || name),
      );
      const row: QuotaRow = {
        name,
        quotaType,
        quotaTypeLabel: QUOTA_TYPE_LABELS[quotaType] ?? quotaType,
        metric,
        metricLabel: QUOTA_METRIC_LABELS[metric] ?? metric,
        limit,
        limitText: thousands(limit),
        mode,
        modeLabel: QUOTA_MODE_LABELS[mode] ?? mode,
        cycleUnit,
        cycleValue,
        cycleLabel: cyclic ? cycleLabelOf(cycleUnit, cycleValue) : null,
        status: prev?.status ?? "enabled",
      };
      const list = readQuotas(ctx).filter(
        (x) => x.name !== name && x.name !== original,
      );
      list.push(row);
      writeRecord(ctx, { quotas: list });
      return { ok: true, data: true };
    },
    /** 删除配额规则（params.name） */
    "policy.removeQuota": (action, ctx) => {
      const name = str(action.params?.name);
      writeRecord(ctx, { quotas: readQuotas(ctx).filter((x) => x.name !== name) });
      return { ok: true, data: true };
    },
    /** 保存限流规则（同 upsertQuota；scope=routes 时取暂存区路由） */
    "policy.upsertRateLimit": (action, ctx) => {
      const p = action.params ?? {};
      const v = (p.rateLimit ?? {}) as Record<string, unknown>;
      const name = str(v.name);
      if (!name) return { ok: false };
      const limitType = str(v.limitType) || "RPM";
      const limit = num(v.limit) ?? 0;
      const burst = num(v.burst) ?? null;
      const scopeAll = str(v.scope) !== "routes";
      const pending = readPendingScope(ctx);
      const original = str(p.original);
      const prev = readRateLimits(ctx).find(
        (x) => x.name === (original || name),
      );
      const row: RateLimitRow = {
        name,
        limitType,
        limitTypeLabel: RATE_LIMIT_TYPE_LABELS[limitType] ?? limitType,
        limit,
        limitText: thousands(limit),
        burst: limitType === "CONCURRENT" ? null : burst,
        scopeAll,
        scopeRoutes: scopeAll ? [] : pending.map((r) => r.routeId),
        scopeLabel: scopeAll
          ? "全部授权路由"
          : pending.length === 0
            ? "—"
            : pending.map((r) => r.name).join("、"),
        status: prev?.status ?? "enabled",
      };
      const list = readRateLimits(ctx).filter(
        (x) => x.name !== name && x.name !== original,
      );
      list.push(row);
      writeRecord(ctx, { rateLimits: list });
      return { ok: true, data: true };
    },
    /** 删除限流规则（params.name） */
    "policy.removeRateLimit": (action, ctx) => {
      const name = str(action.params?.name);
      writeRecord(ctx, {
        rateLimits: readRateLimits(ctx).filter((x) => x.name !== name),
      });
      return { ok: true, data: true };
    },
    /**
     * 统一提交（client / clientTemplate 的 create / update 四态）：
     * values（${event} 表单值）+ state.record（授权路由 / 配额 / 限流）合并。
     * mode: clientCreate | clientUpdate | templateCreate | templateUpdate。
     * clientUpdate 先 PUT 主记录，再 PUT grantsUrl（route-grants）。
     * 成功 data 为响应体；失败 {ok:false,error}（与内置 request 同形）。
     */
    "policy.submit": async (action, ctx) => {
      const p = action.params ?? {};
      const mode = str(p.mode) || "clientCreate";
      const values = { ...((p.values as Record<string, unknown>) ?? {}) };
      const record = readRecord(ctx);
      const routeIds =
        ((record.authRoutes as ScopeRouteRow[]) ?? []).map((r) => r.routeId);
      const quotaSpec = quotaSpecOf(readQuotas(ctx));
      const rateLimitSpec = rateLimitSpecOf(readRateLimits(ctx));
      const tags = str(values.tags)
        .split(/[,，;；·]/)
        .map((t) => t.trim())
        .filter(Boolean);

      const post = async (url: string, method: string, body: unknown) => {
        const response = await fetch(url, {
          method,
          headers: { "content-type": "application/json" },
          credentials: "include",
          body: JSON.stringify(body),
        });
        const data = (await response.json().catch(() => null)) as unknown;
        if (!response.ok) {
          const err = data as { error?: string; message?: string } | null;
          throw new Error(err?.error ?? err?.message ?? `HTTP ${response.status}`);
        }
        return data;
      };

      try {
        if (mode === "clientCreate") {
          const data = await post(str(p.url), "POST", {
            clientCode: values.code,
            displayName: values.name,
            expiresAt: values.expiresAt,
            tags,
            routeIds,
            quotaSpec,
            rateLimitSpec,
            // 模板创建时后端按模板覆盖授权/配额/限流/有效期
            ...(str(p.sourceTemplateCode)
              ? { sourceTemplateCode: str(p.sourceTemplateCode) }
              : {}),
          });
          return { ok: true, data };
        }
        if (mode === "clientUpdate") {
          const data = await post(str(p.url), "PUT", {
            displayName: values.name,
            quotaSpec,
            rateLimitSpec,
            tags,
          });
          if (str(p.grantsUrl)) {
            await post(str(p.grantsUrl), "PUT", { routeIds });
          }
          return { ok: true, data };
        }
        // 模板：create / update 同形（update 无 templateCode；routeIds 随主记录）
        const body: Record<string, unknown> = {
          displayName: values.name,
          description: values.description ?? "",
          tags,
          validityType: values.validityType,
          validityDays: num(values.validityDays) ?? null,
          validityFixedDate: values.validityFixedDate ?? null,
          routeIds,
          quotaSpec,
          rateLimitSpec,
        };
        if (mode === "templateCreate") {
          body.templateCode = values.code;
        }
        const data = await post(
          str(p.url),
          mode === "templateCreate" ? "POST" : "PUT",
          body,
        );
        return { ok: true, data };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
    /**
     * 详情页即时落库（无大表单提交按钮的场景）：把 state.record 的
     * 配额 / 限流（kind=template 时连同授权路由）PUT 到 url；grantsUrl
     * 存在时再 PUT route-grants。displayName/tags 等null字段后端保持不变。
     */
    "policy.persist": async (action, ctx) => {
      const p = action.params ?? {};
      const record = readRecord(ctx);
      const routeIds =
        ((record.authRoutes as ScopeRouteRow[]) ?? []).map((r) => r.routeId);
      const quotaSpec = quotaSpecOf(readQuotas(ctx));
      const rateLimitSpec = rateLimitSpecOf(readRateLimits(ctx));
      const put = async (url: string, body: unknown) => {
        const response = await fetch(url, {
          method: "PUT",
          headers: { "content-type": "application/json" },
          credentials: "include",
          body: JSON.stringify(body),
        });
        if (!response.ok) {
          const err = (await response.json().catch(() => ({}))) as {
            error?: string;
            message?: string;
          };
          throw new Error(err.error ?? err.message ?? `HTTP ${response.status}`);
        }
      };
      try {
        const body: Record<string, unknown> = { quotaSpec, rateLimitSpec };
        if (str(p.kind) === "template") {
          body.routeIds = routeIds;
        }
        await put(str(p.url), body);
        if (str(p.grantsUrl)) {
          await put(str(p.grantsUrl), { routeIds });
        }
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
  };
}

/** 通用工具动作（剪贴板；DSL 无 navigator / JSON.stringify 能力） */
function createUtilActions(): Record<string, DebugAction> {
  return {
    /**
     * 动态默认时间范围：按浏览器本地当天计算 [今天-(days-1), 今天] 并写入
     * state（默认键 range）。DSL 的 state 初始值与表达式引擎都无法取当前
     * 日期（无 now()/today()，state 为静态声明），故由宿主动作补上；ops /
     * audit 页在 lifecycle.onInit 调用本动作，再 reload 对应 dataSource。
     * params.days：天数（缺省 1 = 仅当天；7 = 近 7 天）；params.key：state 键。
     */
    "util.defaultRange": (action, ctx) => {
      const days = Math.max(1, Math.floor(num(action.params?.days) ?? 1));
      const key = str(action.params?.key) || "range";
      const pad = (n: number) => String(n).padStart(2, "0");
      const iso = (d: Date) =>
        `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
      const end = new Date();
      const start = new Date(
        end.getFullYear(),
        end.getMonth(),
        end.getDate() - (days - 1),
      );
      ctx.context.state.set({ [key]: { start: iso(start), end: iso(end) } });
      return { ok: true, data: true };
    },
    /**
     * 不失败的 request（与内置 request 同 params：method/url/body）。
     * 内置 request HTTP 失败返回 {ok:false}，而内置 if 在 then 分支内首个
     * 非 ok 子动作处短路 return——确认弹窗 then 分支里的「request + 错误
     * 提示 if」结构中，request 失败会让错误提示成为死代码（静默失败）。
     * 本动作恒返回 {ok:true}：成功 data=响应体（R 信封，.ok 为 undefined，
     * 与既有 `${actions.x.ok == false}` 判断兼容）；失败
     * data={ok:false,error:{message}}（与 `${actions.x.error.message}` 兼容）。
     */
    "util.request": async (action) => {
      const p = action.params ?? {};
      try {
        const hasBody = p.body !== undefined && p.body !== null;
        const response = await fetch(str(p.url), {
          method: str(p.method) || "GET",
          headers: hasBody ? { "content-type": "application/json" } : undefined,
          credentials: "include",
          body: hasBody ? JSON.stringify(p.body) : undefined,
        });
        const data = (await response.json().catch(() => null)) as unknown;
        if (!response.ok) {
          const err = data as { error?: string; message?: string } | null;
          return {
            ok: true,
            data: {
              ok: false,
              error: {
                // R 信封用 message；Spring 默认错误体 message 比 error 更具体；
                // 前端代理 {error:...} 兜底
                message: err?.message ?? err?.error ?? `HTTP ${response.status}`,
              },
            },
          };
        }
        return { ok: true, data: data ?? { ok: true } };
      } catch (e) {
        return {
          ok: true,
          data: {
            ok: false,
            error: { message: e instanceof Error ? e.message : String(e) },
          },
        };
      }
    },
    /** 复制文本（params.text） */
    "util.copy": (action) => {
      const text = action.params?.text;
      if (typeof text === "string" && text) {
        void navigator.clipboard?.writeText(text);
      }
      return { ok: true, data: true };
    },
    /** 复制 JSON 原文（params.data 为已求值对象） */
    "util.copyJson": (action) => {
      try {
        void navigator.clipboard?.writeText(
          JSON.stringify(action.params?.data ?? null, null, 2),
        );
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
    /**
     * 导出 CSV（params.filename / params.rows 对象数组，表头取首行键序；
     * params.headers 可指定 [{key,label}] 与列序）。DSL 无 Blob/下载能力，在此桥接。
     */
    "util.exportCsv": (action) => {
      try {
        const p = action.params ?? {};
        const rows = (p.rows as Record<string, unknown>[]) ?? [];
        const headers =
          (p.headers as { key: string; label: string }[] | undefined) ??
          Object.keys(rows[0] ?? {}).map((k) => ({ key: k, label: k }));
        const esc = (v: unknown) => {
          const s = v == null ? "" : String(v);
          return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
        };
        const lines = [
          headers.map((h) => esc(h.label)).join(","),
          ...rows.map((r) => headers.map((h) => esc(r[h.key])).join(",")),
        ];
        // BOM 让 Excel 按 UTF-8 打开中文不乱码
        const blob = new Blob(["﻿" + lines.join("\n")], {
          type: "text/csv;charset=utf-8",
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = str(p.filename) || "export.csv";
        a.click();
        URL.revokeObjectURL(url);
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
  };
}

/**
 * 供应商模型拉取 / 导入动作（provider-detail）：DSL 无数组与循环能力，
 * 勾选暂存与批量创建收敛在此。创建走 POST /api/d/models（DRAFT 草稿）。
 */
interface PulledModelRow {
  id: string;
  name: string;
}

/** 上游模型 ID / 名称 → 平台模型编码（小写，保留 - / /，其他字符归并为 -）。 */
function slugModelCode(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\-\/]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

function createProviderModelActions(): Record<string, DebugAction> {
  const readSelected = (ctx: DebugActionContext): PulledModelRow[] =>
    (ctx.context.state.get("pullSelected") as PulledModelRow[]) ?? [];

  const createModel = async (body: Record<string, unknown>) => {
    const response = await fetch("/api/d/models", {
      method: "POST",
      headers: { "content-type": "application/json" },
      credentials: "include",
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const err = (await response.json().catch(() => ({}))) as {
        error?: string;
        message?: string;
      };
      throw new Error(err.error ?? err.message ?? `HTTP ${response.status}`);
    }
  };

  return {
    /** 打开拉取弹层时重置勾选 */
    "providerModels.initPull": (_action, ctx) => {
      ctx.context.state.set({ pullSelected: [] });
      return { ok: true, data: true };
    },
    /** 勾选 / 取消一行（params: {id, name, checked}） */
    "providerModels.toggle": (action, ctx) => {
      const id = str(action.params?.id);
      if (!id) return { ok: false };
      const checked = action.params?.checked === true;
      let list = readSelected(ctx).filter((r) => r.id !== id);
      if (checked) {
        list = [...list, { id, name: str(action.params?.name) || id }];
      }
      ctx.context.state.set({ pullSelected: list });
      return { ok: true, data: true };
    },
    /** 批量创建选中项（params.providerCode）；data={created, failures} */
    "providerModels.importSelected": async (action, ctx) => {
      const providerCode = str(action.params?.providerCode);
      const selected = readSelected(ctx);
      const failures: { id: string; error: string }[] = [];
      let created = 0;
      for (const row of selected) {
        try {
          // 目录约定 DB 存 provider/model（斜杠；UI 层归一化 ~ 展示）
          await createModel({
            modelCode: `${providerCode}/${slugModelCode(row.id)}`,
            displayName: row.name,
            modelType: "LLM",
            primaryProviderCode: providerCode,
            primaryProviderModelId: row.id,
          });
          created += 1;
        } catch (e) {
          failures.push({
            id: row.id,
            error: e instanceof Error ? e.message : String(e),
          });
        }
      }
      ctx.context.state.set({ pullSelected: [] });
      // 失败项作为数据返回（非动作失败），供 DSL 分别提示成功/失败
      return { ok: true, data: { created, failures } };
    },
    /** 导入单个模型（params.values 表单值 + params.providerCode） */
    "providerModels.importOne": async (action) => {
      const p = action.params ?? {};
      const v = (p.values ?? {}) as Record<string, unknown>;
      const name = str(v.modelName);
      const upstreamId = str(v.providerModelId);
      if (!name || !upstreamId) return { ok: false };
      try {
        // modelCode 留空时按 provider/Name 生成（DB 斜杠约定）；CJK 名 slug 为空回退上游 ID
        const code = str(v.modelCode)
          ? slugModelCode(str(v.modelCode))
          : `${str(p.providerCode)}/${slugModelCode(name) || slugModelCode(upstreamId)}`;
        await createModel({
          modelCode: code,
          displayName: name,
          modelType: str(v.modelType) || "LLM",
          primaryProviderCode: str(p.providerCode),
          primaryProviderModelId: upstreamId,
        });
        return { ok: true, data: true };
      } catch (e) {
        return { ok: false, error: e instanceof Error ? e : new Error(String(e)) };
      }
    },
  };
}

/**
 * SitemapApp 全局动作注册表（clientDebug.* / modelDebug.* / routeCandidates.* / clientRoutes.*）。
 * 单例：各组动作名不相交；同一时刻 PageOutlet 只挂载一个页面，
 * 各页面的流式停止闭包互不干扰。
 */
export const pactorActions: Record<string, ActionHandler> = {
  ...createClientDebugActions(),
  ...createModelDebugActions(),
  ...createRouteCandidateActions(),
  ...createClientRouteActions(),
  ...createPolicyActions(),
  ...createUtilActions(),
  ...createProviderModelActions(),
};
