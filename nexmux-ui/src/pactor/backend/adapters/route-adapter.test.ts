/**
 * route-adapter 黄金测试：后端 VO 样例 → UI 形状与 mock 契约逐字段一致。
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

import { getRouteByCode, listRoutes, publishRoute } from "./route-adapter";

const MODEL_VO = {
  modelId: "md-1",
  modelCode: "qwen3.5-flash",
  displayName: "Qwen3.5 Flash",
  status: "ENABLED",
  primaryProviderId: "pv-1",
  primaryProviderModelId: "qwen3.5-flash",
};

const PROVIDER_VO = {
  providerId: "pv-1",
  providerCode: "dashscope",
  displayName: "阿里云百炼",
};

const SPEC = {
  alias: "general",
  ingress: "/v1/chat/completions",
  strategyType: "FIXED",
  candidates: [
    { modelCode: "qwen3.5-flash", weight: 100, enabled: true, priority: "P0", costRate: 1.0, billingRate: 1.2 },
  ],
  filterUnhealthyBindings: true,
  schemaVersion: 1,
  description: "高频通用对话入口",
  protocol: "OpenAI / Chat Completions",
  tags: ["general", "low-cost"],
  stickySession: false,
};

const ROUTE_VO = {
  routeId: "rt-1",
  routeCode: "general-chat",
  alias: "general",
  displayName: "通用对话路由",
  status: "PUBLISHED",
  routeSpec: SPEC,
};

const DRAFT_ROUTE_VO = {
  routeId: "rt-2",
  routeCode: "vision-router",
  alias: "vision",
  displayName: "视觉模型条件路由",
  status: "DRAFT",
  routeSpec: { ...SPEC, candidates: [SPEC.candidates[0], { modelCode: "m2", weight: 50 }], tags: ["vision"] },
};

const DETAIL_VO = {
  ...ROUTE_VO,
  publishedRouteSpec: SPEC,
  publishedBy: "zhangsan",
  createdAt: "2026-06-10T15:00:00",
  updatedAt: "2026-09-08T11:05:00",
  updatedBy: "lisi",
};

function stubAll() {
  getMock.mockImplementation((path: string) => {
    if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO, DRAFT_ROUTE_VO] });
    if (path === "/api/nexmux/models") return Promise.resolve({ records: [MODEL_VO] });
    if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
    if (path === "/api/nexmux/routes/by-code/general-chat") return Promise.resolve(DETAIL_VO);
    return Promise.resolve({ records: [] });
  });
}

describe("listRoutes", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("组装 items/counts/filters（卡片派生字段齐全）", async () => {
    const result = await listRoutes({ status: "all" });

    expect(result.total).toBe(2);
    const item = result.items[0];
    expect(item.code).toBe("general-chat");
    expect(item.type).toBe("single");
    expect(item.typeLabel).toBe("单模型");
    expect(item.status).toBe("enabled");
    expect(item.statusLabel).toBe("启用");
    expect(item.path).toBe("/v1/chat/completions");
    expect(item.protocol).toBe("OpenAI / Chat Completions");
    expect(item.operation).toBe("chat.completions");
    expect(item.entrySummary).toBe("/v1/chat/completions · OpenAI / Chat Completions");
    expect(item.policySummary).toBe("1 模型 · 固定");
    expect(item.tags).toEqual(["general", "low-cost"]);
    expect(item.models[0]).toMatchObject({
      modelCode: "qwen3.5-flash",
      modelName: "Qwen3.5 Flash",
      providerSummary: "阿里云百炼 · qwen3.5-flash",
      weight: 100,
      priority: "P0",
      costRate: 1.0,
      billingRate: 1.2,
      status: "enabled",
    });
    // draft 项
    expect(result.items[1].status).toBe("draft");
    expect(result.items[1].type).toBe("pool");
    // counts / filters 与 mock 文案逐字一致
    expect(result.counts.all).toEqual({ enabled: 1, total: 2 });
    expect(result.filters.status.map((f: { text: string }) => f.text)).toEqual([
      "全部 1/2", "已启用 1", "已停用 0", "草稿 1",
    ]);
    expect(result.filters.type.map((f: { text: string }) => f.text)).toEqual([
      "全部 1/2", "单模型 1/1", "多模型池 0/1", "条件路由 0/0",
    ]);
    expect(result.filters.tags.map((f: { text: string }) => f.text)).toEqual([
      "全部 2", "general 1", "low-cost 1", "vision 1",
    ]);
  });

  it("status 快筛映射：enabled → PUBLISHED 下发后端", async () => {
    await listRoutes({ status: "enabled" });

    const pageCall = getMock.mock.calls.find(
      ([path, params]) =>
        path === "/api/nexmux/routes" &&
        (params as Record<string, unknown>)?.pageSize === 5000,
    );
    expect(pageCall?.[1]).toMatchObject({ status: "PUBLISHED" });
  });
});

describe("getRouteByCode", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("详情 VO → RouteRecord（规格与审计字段齐全）", async () => {
    const record = await getRouteByCode("general-chat");

    expect(record).not.toBeNull();
    expect(record!.description).toBe("高频通用对话入口");
    expect(record!.stickySession).toBe(false);
    expect(record!.healthFilter).toBe(true);
    expect(record!.strategyLabel).toBe("固定");
    expect(record!.strategySummary).toBe("固定 (FIXED)");
    expect(record!.tagsText).toBe("general · low-cost");
    expect(record!.enabledModelCount).toBe(1);
    expect(record!.weightTotal).toBe(100);
    expect(record!.createdAt).toBe("2026-06-10 15:00");
    expect(record!.shortDate).toBe("09-08");
    expect(record!.operator).toBe("lisi");
  });
});

describe("publishRoute", () => {
  it("code → id 解析后调发布端点", async () => {
    getMock.mockReset();
    stubAll();
    postMock.mockResolvedValue(ROUTE_VO);

    await publishRoute("general-chat", { changeSummary: "首次发布" });

    expect(postMock).toHaveBeenCalledWith("/api/nexmux/routes/rt-1/publish", {
      changeSummary: "首次发布",
    });
  });
});
