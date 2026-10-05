/**
 * 页面 DSL 的 service 数据源注册表（真实后端实现）。
 *
 * 经 SitemapApp 的 `services` prop 全局注册，与服务端路由
 * app/api/data/[...parts]/route.ts 复用同一套包装层。
 * 实现在 src/pactor/backend/（BFF：client + adapters + services-real）；
 * mock 已按设计 §10 里程碑退役删除（docs/design/nexmux-ui-data-api-design.md）。
 *
 * 列表类服务负责把 DSL 的一族布尔快筛参数还原为逗号串（表达式引擎不支持数组方法）。
 */

import type { ServiceHandler } from "@pactor-app/runtime";

import { realServices } from "./backend/services-real";

/** 快筛值数组：service 直调为数组，http query 序列化后为逗号串；空值返回 undefined */
function strList(value: unknown): string[] | undefined {
  if (Array.isArray(value)) {
    return value.filter((v): v is string => typeof v === "string" && v !== "");
  }
  if (typeof value === "string") return value.split(",").filter(Boolean);
  return undefined;
}

export const pactorServices: Record<string, ServiceHandler> = {
  "dashboard.query": (params) => realServices["dashboard.query"](params),

  // 类型多选：ChipFilter 以 types 数组（http 侧序列化为逗号串）提交；
  // 旧版 DSL 一族一个布尔参数的写法保留兼容，这里还原为 family 逗号串
  "provider.list": (params) =>
    realServices["provider.list"]({
      ...params,
      types:
        strList(params.types) ??
        ["openai", "anthropic", "gemini", "dashscope"]
          .filter((f) => params[f] === true)
          .join(","),
    }),
  "provider.detail": (params) => realServices["provider.detail"](params),
  "provider.pullModels": (params) => realServices["provider.pullModels"](params),

  // 厂商多选：ChipFilter 以 providers 数组（http 侧为逗号串）提交；旧布尔族写法保留兼容
  "model.list": (params) =>
    realServices["model.list"]({
      ...params,
      providers:
        strList(params.providers) ??
        ["dashscope", "gemini", "openai"].filter((p) => params[p] === true).join(","),
    }),
  "model.detail": (params) => realServices["model.detail"](params),

  // 标签多选：ChipFilter 以 tags 数组（http 侧为逗号串）提交；旧布尔族写法保留兼容
  "route.list": (params) =>
    realServices["route.list"]({
      ...params,
      tags:
        strList(params.tags) ??
        [
          ["reasoning", "tag_reasoning"],
          ["premium", "tag_premium"],
          ["general", "tag_general"],
          ["coding", "tag_coding"],
          ["low-cost", "tag_low_cost"],
          ["mobile", "tag_mobile"],
          ["vision", "tag_vision"],
          ["embedding", "tag_embedding"],
        ]
          .filter(([, k]) => params[k] === true)
          .map(([t]) => t)
          .join(","),
    }),
  "route.detail": (params) => realServices["route.detail"](params),

  "client.list": (params) => realServices["client.list"](params),
  "client.detail": (params) => realServices["client.detail"](params),

  "clientTemplate.list": (params) => realServices["clientTemplate.list"](params),
  "clientTemplate.detail": (params) => realServices["clientTemplate.detail"](params),
  "clientTemplate.createdClients": (params) =>
    realServices["clientTemplate.createdClients"](params),

  "callLog.list": (params) => realServices["callLog.list"](params),
  "callLog.detail": (params) => realServices["callLog.detail"](params),

  "cost.list": (params) => realServices["cost.list"](params),
  "cost.detail": (params) => realServices["cost.detail"](params),

  "audit.list": (params) => realServices["audit.list"](params),
  "audit.detail": (params) => realServices["audit.detail"](params),
  "audit.operators": () => realServices["audit.operators"](),

  "ops.modelUsage": (params) => realServices["ops.modelUsage"](params),
  "ops.providerUsage": (params) => realServices["ops.providerUsage"](params),
  "ops.clientUsage": (params) => realServices["ops.clientUsage"](params),
  "ops.availability": (params) => realServices["ops.availability"](params),
};
