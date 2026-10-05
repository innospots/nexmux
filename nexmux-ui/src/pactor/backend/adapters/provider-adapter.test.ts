/**
 * provider-adapter 黄金测试：后端 VO 样例 → UI 形状与 mock 契约逐字段一致。
 * 设计依据：docs/design/nexmux-ui-data-api-design.md §11（BFF 测试范围）。
 */

import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ProviderRecord } from "../../mocks/providers-mock";

const getMock = vi.fn();
const postMock = vi.fn();
const putMock = vi.fn();

vi.mock("../client", () => ({
  backendClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => putMock(...args),
  },
  fetchAll: (path: string) => getMock(path, { pageNo: 1, pageSize: 500 }).then(
    (page: { records: unknown[] }) => page.records,
  ),
  BackendError: class BackendError extends Error {
    constructor(
      public code: string,
      message: string,
      public httpStatus = 502,
    ) {
      super(message);
    }
  },
}));

import {
  createProvider,
  getProviderByCode,
  listProviders,
  pullProviderModels,
  updateProvider,
} from "./provider-adapter";

const PROVIDER_VO = {
  providerId: "pv-1",
  providerCode: "dashscope",
  displayName: "阿里云百炼",
  providerType: "OPENAI",
  defaultProtocol: "OpenAI / Chat Completions",
  vendorCode: "dashscope",
  sourceVendorCode: "dashscope",
  baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
  region: "cn-hangzhou",
  timeoutMs: 60000,
  status: "ENABLED",
  keyStatus: "configured",
  modelCount: 2,
};

const DETAIL_VO = {
  ...PROVIDER_VO,
  proxy: "",
  description: "阿里云大模型服务平台",
  capabilityExtension: '["chat","vision"]',
  keyVersion: 2,
  rotatedAt: "2026-09-01T10:00:00",
  models: [
    {
      modelCode: "qwen3.5-flash",
      displayName: "Qwen3.5 Flash",
      modelType: "LLM",
      providerModelId: "qwen3.5-flash",
      status: "ENABLED",
    },
    {
      modelCode: "qwen3-vl-plus",
      displayName: "Qwen3-VL Plus",
      modelType: "LLM",
      providerModelId: "qwen-vl-plus-latest",
      status: "DISABLED",
    },
  ],
  createdAt: "2026-06-01T10:00:00",
  updatedAt: "2026-09-10T09:30:00",
  updatedBy: "zhangsan",
};

/** 关联数据（models / routes 全量拉取） */
function stubRelations() {
  getMock.mockImplementation((path: string) => {
    if (path === "/api/nexmux/models") {
      return Promise.resolve({
        records: [
          { modelCode: "qwen3.5-flash", primaryProviderId: "pv-1", status: "ENABLED" },
          { modelCode: "qwen3-vl-plus", primaryProviderId: "pv-1", status: "DISABLED" },
        ],
      });
    }
    if (path === "/api/nexmux/routes") {
      return Promise.resolve({
        records: [
          {
            status: "PUBLISHED",
            routeSpec: { candidates: [{ modelCode: "qwen3.5-flash", weight: 100 }] },
          },
        ],
      });
    }
    return Promise.resolve({ records: [] });
  });
}

describe("listProviders", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
  });

  it("组装 items/total/counts/filters 形状", async () => {
    getMock.mockImplementation((path: string, params?: Record<string, unknown>) => {
      if (path === "/api/nexmux/providers" && !params?.status && !params?.keyStatus) {
        // 筛选页与 fetchAll（counts 全量）同端点：均返回单条
        return Promise.resolve({ records: [PROVIDER_VO], total: 1, pages: 1 });
      }
      if (path === "/api/nexmux/models") return Promise.resolve({ records: [] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [] });
      return Promise.resolve({ records: [] });
    });

    const result = await listProviders({ status: "all" });

    // total 为筛选后总数（后端 PageResult.total）
    expect(result.total).toBe(1);
    expect(result.pageNo).toBe(1);
    expect(result.pageSize).toBe(50);
    expect(result.pages).toBe(1);
    expect(result.items).toHaveLength(1);
    const item: ProviderRecord = result.items[0];
    expect(item.code).toBe("dashscope");
    expect(item.icon).toBe("cloud");
    expect(item.family).toBe("dashscope");
    expect(item.status).toBe("enabled");
    expect(item.keyStatus).toBe("configured");
    expect(item.routeSummary).toBe("未关联路由");
    // counts / filters 与 mock 文案逐字一致
    expect(result.counts.all).toEqual({ enabled: 1, total: 1 });
    expect(result.filters.status).toEqual([
      { value: "all", text: "全部 1/1" },
      { value: "enabled", text: "已启用 1", dot: "#22c55e" },
      { value: "disabled", text: "已停用 0", dot: "#9ca3af" },
      { value: "pending", text: "待填密钥 0", dot: "#f59e0b" },
      { value: "none", text: "无密钥 0", dot: "#ef4444" },
    ]);
    // 动态厂商 chips：按数据中出现的 vendor 生成（总数降序），已知厂商带 dot
    expect(result.filters.types).toEqual([
      { value: "dashscope", text: "阿里云百炼 1/1", dot: "#8b5cf6" },
    ]);
  });

  it("status 快筛拆分：pending → keyStatus 参数", async () => {
    stubRelations();
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [] });
      return Promise.resolve({ records: [] });
    });

    await listProviders({ status: "pending" });

    // 分页 items 请求（pageSize 50）；fetchAll 全量请求由 mock 固定 pageSize 500
    const pageCall = getMock.mock.calls.find(
      ([path, params]) =>
        path === "/api/nexmux/providers" &&
        (params as Record<string, unknown>)?.pageSize === 50,
    );
    expect(pageCall?.[1]).toMatchObject({ keyStatus: "pending" });
    expect((pageCall?.[1] as Record<string, unknown>).status).toBeUndefined();
  });

  it("分页透传：page/pageSize 下发后端，total/pages 取自 PageResult", async () => {
    getMock.mockImplementation((path: string, params?: Record<string, unknown>) => {
      if (path === "/api/nexmux/providers" && params?.pageNo === 2) {
        return Promise.resolve({ records: [PROVIDER_VO], total: 42, pages: 42 });
      }
      if (path === "/api/nexmux/models") return Promise.resolve({ records: [] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [] });
      return Promise.resolve({ records: [], total: 0, pages: 0 });
    });

    const result = await listProviders({ status: "all", page: 2, pageSize: 1 });

    const pageCall = getMock.mock.calls.find(
      ([path, params]) =>
        path === "/api/nexmux/providers" &&
        (params as Record<string, unknown>)?.pageSize === 1,
    );
    expect(pageCall?.[1]).toMatchObject({ pageNo: 2, pageSize: 1 });
    expect(result.items).toHaveLength(1);
    expect(result.total).toBe(42);
    expect(result.pageNo).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.pages).toBe(42);
  });
});

describe("getProviderByCode", () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it("详情 VO → ProviderRecord（派生字段齐全）", async () => {
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/providers/by-code/dashscope") {
        return Promise.resolve(DETAIL_VO);
      }
      if (path === "/api/nexmux/models") {
        return Promise.resolve({
          records: [
            { modelCode: "qwen3.5-flash", primaryProviderId: "pv-1", status: "ENABLED" },
          ],
        });
      }
      if (path === "/api/nexmux/routes") {
        return Promise.resolve({
          records: [
            {
              status: "PUBLISHED",
              routeSpec: { candidates: [{ modelCode: "qwen3.5-flash", weight: 100 }] },
            },
          ],
        });
      }
      return Promise.resolve({ records: [] });
    });

    const record = await getProviderByCode("dashscope");

    expect(record).not.toBeNull();
    expect(record!.code).toBe("dashscope");
    expect(record!.maskedKey).toBe("•••••••• (v2)");
    expect(record!.capabilities).toEqual(["chat", "vision"]);
    expect(record!.models).toHaveLength(2);
    expect(record!.models[0]).toMatchObject({
      code: "qwen3.5-flash",
      status: "ONLINE",
      routes: 1,
    });
    expect(record!.models[1].status).toBe("OFFLINE");
    expect(record!.enabledModelCount).toBe(1);
    expect(record!.routes).toEqual({ total: 1, enabled: 1 });
    expect(record!.routeSummary).toBe("1 条路由 · 1 条启用");
    expect(record!.createdAt).toBe("2026-06-01 10:00");
    expect(record!.operator).toBe("zhangsan");
  });
});

describe("createProvider", () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it("vendorCode 缺省回填 providerCode，契约外字段落桩忽略", async () => {
    postMock.mockResolvedValue(PROVIDER_VO);

    await createProvider({
      providerCode: "dashscope",
      displayName: "阿里云百炼",
      providerType: "COMMERCIAL",
      defaultProtocol: "openai-completions",
      baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
      timeoutMs: 60000,
      // 契约外字段（表单整包提交带入）
      apiKey: "sk-secret",
      description: "不应转发",
    });

    expect(postMock).toHaveBeenCalledWith("/api/nexmux/providers", {
      providerCode: "dashscope",
      displayName: "阿里云百炼",
      providerType: "COMMERCIAL",
      defaultProtocol: "openai-completions",
      vendorCode: "dashscope",
      sourceVendorCode: "dashscope",
      iconUri: undefined,
      baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
      region: undefined,
      timeoutMs: 60000,
    });
    const payload = postMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload).not.toHaveProperty("apiKey");
    expect(payload).not.toHaveProperty("description");
  });

  it("显式 vendorCode 不被回填覆盖", async () => {
    postMock.mockResolvedValue(PROVIDER_VO);

    await createProvider({
      providerCode: "my-openai",
      displayName: "My OpenAI",
      vendorCode: "openai",
    });

    const payload = postMock.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.vendorCode).toBe("openai");
    expect(payload.sourceVendorCode).toBe("openai");
  });
});

describe("updateProvider", () => {
  beforeEach(() => {
    getMock.mockReset();
    putMock.mockReset();
  });

  it("只转发 ProviderUpdateRequest 契约字段", async () => {
    getMock.mockResolvedValue(DETAIL_VO);
    putMock.mockResolvedValue(PROVIDER_VO);

    await updateProvider("dashscope", {
      displayName: "新名字",
      baseUrl: "https://example.com",
      region: "cn-hangzhou",
      timeoutMs: 30000,
      // 契约外字段
      status: "disabled",
      apiKey: "sk-secret",
    } as Parameters<typeof updateProvider>[1]);

    expect(getMock).toHaveBeenCalledWith("/api/nexmux/providers/by-code/dashscope");
    expect(putMock).toHaveBeenCalledWith("/api/nexmux/providers/pv-1", {
      displayName: "新名字",
      baseUrl: "https://example.com",
      region: "cn-hangzhou",
      timeoutMs: 30000,
    });
  });
});

describe("pullProviderModels", () => {
  it("existsLocally → local 文案", async () => {
    getMock.mockImplementation((path: string) => {
      if (path.startsWith("/api/nexmux/providers/by-code/")) {
        return Promise.resolve(DETAIL_VO);
      }
      return Promise.resolve({ records: [] });
    });
    postMock.mockResolvedValue([
      { providerModelId: "qwen3.5-flash", existsLocally: true },
      { providerModelId: "qwen3-max", existsLocally: false },
    ]);

    const rows = await pullProviderModels("dashscope");

    expect(postMock).toHaveBeenCalledWith("/api/nexmux/providers/pv-1/pull-models");
    expect(rows).toEqual([
      { id: "qwen3.5-flash", name: "qwen3.5-flash", local: "已存在" },
      { id: "qwen3-max", name: "qwen3-max", local: "新增" },
    ]);
  });
});
