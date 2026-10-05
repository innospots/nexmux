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
import { queryDashboard } from "./mocks/dashboard-mock";
import {
  getProvider,
  pullProviderModels,
  queryProviders,
} from "./mocks/providers-mock";
import { getModel, queryModels } from "./mocks/models-mock";
import { getRoute, queryRoutes } from "./mocks/routes-mock";
import {
  CLIENT_TAG_FILTERS,
  clientsByTemplate,
  getClient,
  queryClients,
} from "./mocks/clients-mock";
import {
  CLIENT_TEMPLATE_TAG_FILTERS,
  getClientTemplate,
  queryClientTemplates,
} from "./mocks/client-templates-mock";
import { getCallLog, queryCallLogs } from "./mocks/call-logs-mock";
import { getCost, queryCosts } from "./mocks/costs-mock";
import { getAudit, listAuditOperators, queryAudits } from "./mocks/audit-mock";
import {
  queryAvailability,
  queryClientUsage,
  queryModelUsage,
  queryProviderUsage,
} from "./mocks/ops-usage-mock";

function str(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function opt(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

/** 分页参数：字符串 query 转数字，非正整数落缺省（undefined → 不分页 / adapter 默认） */
function num(value: unknown): number | undefined {
  const n = typeof value === "string" ? Number(value) : typeof value === "number" ? value : NaN;
  return Number.isInteger(n) && n >= 1 ? n : undefined;
}

/** 标签 state 键：连字符转下划线（如 team-default → tag_team_default） */
const tagKey = (value: string) => `tag_${value.replace(/-/g, "_")}`;

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
    realBackendEnabled()
      ? realServices["provider.list"](params)
      : queryProviders({
          keyword: str(params.keyword),
          status: str(params.status) || "all",
          types:
            strList(params.types) ??
            ["openai", "anthropic", "gemini", "dashscope"].filter(
              (f) => params[f] === true,
            ),
          page: num(params.page),
          pageSize: num(params.pageSize),
        }),
  "provider.detail": (params) =>
    realBackendEnabled()
      ? realServices["provider.detail"](params)
      : getProvider(str(params.code)),
  "provider.pullModels": (params) =>
    realBackendEnabled()
      ? realServices["provider.pullModels"](params)
      : pullProviderModels(str(params.code)),
  /** 表单 Provider 下拉选项（模型新建/编辑页用）：{label, value}[] */
  "provider.options": () =>
    realBackendEnabled()
      ? realServices["provider.options"]()
      : queryProviders({ pageSize: 500 }).items.map((p) => ({
          label: p.name,
          value: p.code,
        })),

  // 厂商多选：ChipFilter 以 providers 数组（http 侧为逗号串）提交；旧布尔族写法保留兼容
  "model.list": (params) =>
    realBackendEnabled()
      ? realServices["model.list"](params)
      : queryModels({
          type: str(params.type) || "all",
          providers:
            strList(params.providers) ??
            ["dashscope", "gemini", "openai"].filter((p) => params[p] === true),
          page: num(params.page),
          pageSize: num(params.pageSize),
        }),
  "model.detail": (params) =>
    realBackendEnabled()
      ? realServices["model.detail"](params)
      : getModel(str(params.code)),

  // 标签多选：ChipFilter 以 tags 数组（http 侧为逗号串）提交；旧布尔族写法保留兼容
  "route.list": (params) =>
    realBackendEnabled()
      ? realServices["route.list"](params)
      : queryRoutes({
          status: str(params.status) || "all",
          type: str(params.type) || "all",
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
              .map(([t]) => t),
          page: num(params.page),
          pageSize: num(params.pageSize),
        }),
  "route.detail": (params) =>
    realBackendEnabled()
      ? realServices["route.detail"](params)
      : getRoute(str(params.code)),

  "client.list": (params) =>
    realBackendEnabled()
      ? realServices["client.list"](params)
      : queryClients({
          status: str(params.status) || "all",
          name: str(params.name),
          code: str(params.code),
          // ChipFilter 以 tags 数组（http 侧为逗号串）提交；旧布尔族写法保留兼容
          tags:
            strList(params.tags) ??
            CLIENT_TAG_FILTERS.filter((t) => params[tagKey(t.value)] === true).map(
              (t) => t.value,
            ),
          page: num(params.page),
          pageSize: num(params.pageSize),
        }),
  "client.detail": (params) =>
    realBackendEnabled()
      ? realServices["client.detail"](params)
      : getClient(str(params.code)),

  "clientTemplate.list": (params) =>
    realBackendEnabled()
      ? realServices["clientTemplate.list"](params)
      : queryClientTemplates({
          status: str(params.status) || "all",
          keyword: str(params.keyword),
          tags:
            strList(params.tags) ??
            CLIENT_TEMPLATE_TAG_FILTERS.filter(
              (t) => params[tagKey(t.value)] === true,
            ).map((t) => t.value),
          page: num(params.page),
          pageSize: num(params.pageSize),
        }),
  "clientTemplate.detail": (params) =>
    realBackendEnabled()
      ? realServices["clientTemplate.detail"](params)
      : getClientTemplate(str(params.code)),
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
