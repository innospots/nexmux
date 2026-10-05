/**
 * 页面 DSL 的 service 数据源注册表（真实后端实现）。
 *
 * 经 SitemapApp 的 `services` prop 全局注册，与服务端路由
 * app/api/data/[...parts]/route.ts 复用同一套包装层。
 * 实现在 src/pactor/backend/（BFF：client + adapters + services-real）；
 * mock 已按设计 §10 里程碑退役删除（docs/design/nexmux-ui-data-api-design.md）。
 *
 * 本层只做服务名 → realServices 的分发；查询参数归一化（字符串/逗号串/分页）
 * 由 realServices 各 handler 自行处理。
 */

import type { ServiceHandler } from "@pactor-app/runtime";

import { realServices } from "./backend/services-real";

export const pactorServices: Record<string, ServiceHandler> = {
  "dashboard.query": (params) => realServices["dashboard.query"](params),

  "provider.list": (params) => realServices["provider.list"](params),
  "provider.detail": (params) => realServices["provider.detail"](params),
  "provider.pullModels": (params) => realServices["provider.pullModels"](params),
  /** 表单 Provider 下拉选项（模型新建/编辑页用）：{label, value}[] */
  "provider.options": () => realServices["provider.options"](),

  "model.list": (params) => realServices["model.list"](params),
  "model.detail": (params) => realServices["model.detail"](params),

  "route.list": (params) => realServices["route.list"](params),
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
