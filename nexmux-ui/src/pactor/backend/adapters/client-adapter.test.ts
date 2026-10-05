/**
 * client-adapter / client-template-adapter 黄金测试。
 */

import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const postMock = vi.fn();
const putMock = vi.fn();

vi.mock("../client", () => ({
  backendClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => putMock(...args),
  },
  fetchAll: (path: string) => getMock(path, { pageNo: 1, pageSize: 5000 }).then(
    (page: { records: unknown[] }) => page.records,
  ),
  BackendError: class BackendError extends Error {},
}));

import { getClientByCode, listClients } from "./client-adapter";
import { getClientTemplateByCode, listClientTemplates } from "./client-template-adapter";

const ROUTE_VO = {
  routeId: "rt-1",
  routeCode: "general-chat",
  alias: "general",
  displayName: "通用对话路由",
  routeSpec: { ingress: "/v1/chat/completions", candidates: [{}] },
};

const TEMPLATE_VO = {
  templateId: "ct-1",
  templateCode: "team-default",
  displayName: "团队默认",
  status: "ENABLED",
  tags: '["team-default"]',
  validityType: "days",
  validityDays: 365,
  validTo: null,
  authorizedRouteIds: ["rt-1"],
  quotaSpec: JSON.stringify([
    { name: "总量", quotaType: "lifetime", metric: "TOTAL_TOKEN", limit: 1000000, mode: "hard", status: "enabled" },
    { name: "日循环", quotaType: "cyclic", metric: "REQUEST", limit: 10000, mode: "soft", cycleUnit: "day", cycleValue: 1, status: "enabled" },
  ]),
  rateLimitSpec: JSON.stringify([
    { name: "默认限流", limitType: "RPM", limit: 1000, scopeAll: true, status: "enabled" },
  ]),
  createdClientCount: 1,
};

const CLIENT_VO = {
  clientId: "cl-1",
  clientCode: "mobile-app",
  displayName: "移动 App",
  status: "ENABLED",
  authorizedRouteIds: ["rt-1"],
  quotaSpec: TEMPLATE_VO.quotaSpec,
  rateLimitSpec: TEMPLATE_VO.rateLimitSpec,
  appKeyVersion: 2,
  appKeyId: "gw-ak-7f3a2b1c",
  tags: '["mobile","team"]',
  sourceTemplateId: "ct-1",
  expiresAt: "2027-01-01T23:59:59",
  appKeyRotatedAt: "2026-09-01T10:00:00",
};

const CLIENT_DETAIL = {
  ...CLIENT_VO,
  tags: ["mobile", "team"],
  createdAt: "2026-06-01T10:00:00",
  updatedAt: "2026-09-10T09:30:00",
  updatedBy: "zhangsan",
};

function stubAll() {
  getMock.mockImplementation((path: string) => {
    if (path === "/api/nexmux/clients") return Promise.resolve({ records: [CLIENT_VO] });
    if (path === "/api/nexmux/client-templates") return Promise.resolve({ records: [TEMPLATE_VO] });
    if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
    if (path === "/api/nexmux/clients/by-code/mobile-app") return Promise.resolve(CLIENT_DETAIL);
    if (path === "/api/nexmux/client-templates/by-code/team-default") {
      return Promise.resolve({
        ...TEMPLATE_VO,
        description: "团队默认策略",
        tags: ["team-default"],
        createdAt: "2026-06-01T10:00:00",
        updatedAt: "2026-09-01T10:00:00",
        updatedBy: "lisi",
      });
    }
    return Promise.resolve({ records: [] });
  });
}

describe("listClients", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("组装 ClientRecord（appKeySummary/配额摘要/模板关联）", async () => {
    const result = await listClients({ status: "all" });

    expect(result.total).toBe(1);
    const item = result.items[0];
    expect(item.code).toBe("mobile-app");
    expect(item.status).toBe("enabled");
    expect(item.statusLabel).toBe("启用");
    expect(item.appKeyId).toBe("gw-ak-7f3a2b1c");
    expect(item.expiresAt).toBe("2027-01-01 23:59");
    expect(item.templateName).toBe("团队默认");
    expect(item.authRoutes[0]).toEqual({
      code: "general-chat",
      name: "通用对话路由",
      alias: "general",
      typeLabel: "单模型",
      path: "/v1/chat/completions",
    });
    expect(item.quotaSummary).toBe("总+日循环");
    expect(item.rateLimitSummary).toBe("1000 RPM");
    expect(item.appKeySummary).toBe("gw-ak-7f3a2b1c · 到期 2027-01-01 · 1 路由 · 1000 RPM");
    expect(result.filters.tags.map((f: { text: string }) => f.text)).toEqual([
      "全部 1", "mobile 1", "team 1",
    ]);
  });
});

describe("getClientByCode", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("详情：配额/限流规则含展示标签，用量字段为空", async () => {
    const record = await getClientByCode("mobile-app");

    expect(record).not.toBeNull();
    expect(record!.quotas[0]).toMatchObject({
      quotaTypeLabel: "总配额",
      metricLabel: "Total Token",
      limitText: "1,000,000",
      modeLabel: "Hard Limit",
      usagePercent: undefined,
    });
    expect(record!.quotas[1].cycleLabel).toBe("每 1 天");
    expect(record!.rateLimits[0]).toMatchObject({
      limitTypeLabel: "RPM（每分钟请求数）",
      scopeLabel: "全部授权路由",
    });
    expect(record!.secretLastRotatedAt).toBe("2026-09-01 10:00");
    expect(record!.operator).toBe("zhangsan");
    expect(record!.tagsText).toBe("mobile · team");
  });
});

describe("client-template-adapter", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("列表：validityLabel/policySummary/filters", async () => {
    const result = await listClientTemplates({ status: "all" });

    expect(result.total).toBe(1);
    const item = result.items[0];
    expect(item.validityLabel).toBe("创建后 365 天");
    expect(item.quotaSummary).toBe("总+日循环");
    expect(item.rateLimitSummary).toBe("1000 RPM");
    expect(item.policySummary).toBe("1 路由 · 总+日循环 · 1000 RPM");
    expect(item.createdClientCount).toBe(1);
    expect(result.filters.status.map((f: { text: string }) => f.text)).toEqual([
      "全部 1/1", "启用 1", "停用 0",
    ]);
  });

  it("详情：描述与审计字段", async () => {
    const record = await getClientTemplateByCode("team-default");

    expect(record).not.toBeNull();
    expect(record!.description).toBe("团队默认策略");
    expect(record!.operator).toBe("lisi");
    expect(record!.authRoutes[0].typeLabel).toBe("单模型");
    expect(record!.quotas[0].limitText).toBe("1,000,000");
  });
});
