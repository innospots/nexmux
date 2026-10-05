/**
 * model-adapter 黄金测试：后端 VO 样例 → UI 形状与 mock 契约逐字段一致。
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
  fetchAll: (path: string) => getMock(path, { pageNo: 1, pageSize: 500 }).then(
    (page: { records: unknown[] }) => page.records,
  ),
  BackendError: class BackendError extends Error {},
}));

import { createModel, getModelByCode, invokeModelTest, listModels, updateModel } from "./model-adapter";

const PROVIDER_VO = {
  providerId: "pv-1",
  providerCode: "dashscope",
  displayName: "阿里云百炼",
  vendorCode: "dashscope",
  keyStatus: "configured",
};

const MODEL_VO = {
  modelId: "md-1",
  modelCode: "qwen3.5-flash",
  displayName: "Qwen3.5 Flash",
  status: "ENABLED",
  modelType: "LLM",
  contextLength: 256000,
  maxOutputTokens: 32000,
  primaryProviderId: "pv-1",
  pricingSpec: JSON.stringify({
    tiers: [{ upto: null, input: 0.2, output: 1.6, cacheRead: null, cacheWrite: null }],
    currency: "USD",
    priceVersion: "v2026-09",
    priceEffective: "2026-09-01",
  }),
  extraBindings: "[]",
};

const DETAIL_VO = {
  ...MODEL_VO,
  releaseDate: "2026-05-01",
  inputModalities: "text,image",
  outputModalities: "text",
  capabilityTags: '["chat","vision"]',
  tags: '["general","low-cost"]',
  description: "轻量通用模型",
  defaultParams: '{"temperature":"0.7","topP":"0.9"}',
  primaryProviderModelId: "qwen3.5-flash",
  primaryBindingWeight: 100,
  primaryCostMultiplier: 1,
  primaryReferenceMultiplier: 1.2,
  primaryBindingEnabled: true,
  createdAt: "2026-06-10T15:00:00",
  updatedAt: "2026-09-08T11:05:00",
  updatedBy: "lisi",
};

const ROUTE_VO = {
  alias: "general",
  displayName: "通用对话路由",
  status: "PUBLISHED",
  routeSpec: { candidates: [{ modelCode: "qwen3.5-flash", weight: 100 }] },
};

describe("listModels", () => {
  beforeEach(() => getMock.mockReset());

  it("组装 items/counts/filters（卡片派生字段齐全）", async () => {
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/models") {
        return Promise.resolve({ records: [MODEL_VO], total: 1, pages: 1 });
      }
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const result = await listModels({ type: "all" });

    // total 为筛选后总数（后端 PageResult.total）
    expect(result.total).toBe(1);
    expect(result.pageNo).toBe(1);
    expect(result.pageSize).toBe(50);
    expect(result.pages).toBe(1);
    const item = result.items[0];
    expect(item.code).toBe("qwen3.5-flash");
    expect(item.status).toBe("enabled");
    expect(item.type).toBe("LLM");
    expect(item.providerIcon).toBe("cloud");
    expect(item.specSummary).toBe("256K · 32K");
    expect(item.pricingSummary).toBe("$0.2 / $1.6 /M");
    expect(item.tierLabel).toBe("");
    expect(item.routes).toEqual({ total: 1, active: 1 });
    expect(item.routeSummary).toBe("1 条路由 · 1 条活跃");
    expect(item.suppliers).toHaveLength(1);
    expect(result.filters.type.map((t: { value: string }) => t.value)).toEqual([
      "all", "LLM", "Image", "Audio", "Embedding",
    ]);
    expect(result.filters.providers[0].text).toBe("全部 1");
    expect(result.filters.providers[1]).toEqual({ value: "dashscope", text: "阿里云百炼 1" });
  });

  it("分页透传：page/pageSize 下发后端，total/pages 取自 PageResult", async () => {
    getMock.mockImplementation((path: string, params?: Record<string, unknown>) => {
      if (path === "/api/nexmux/models" && params?.pageNo === 2) {
        return Promise.resolve({ records: [MODEL_VO], total: 1616, pages: 1616 });
      }
      if (path === "/api/nexmux/models") {
        return Promise.resolve({ records: [MODEL_VO], total: 1616, pages: 1616 });
      }
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const result = await listModels({ type: "all", page: 2, pageSize: 1 });

    // fetchAll（counts 全量）由 mock 固定 pageSize 500，分页 items 请求 pageSize 1
    const pageCall = getMock.mock.calls.find(
      ([path, params]) =>
        path === "/api/nexmux/models" &&
        (params as Record<string, unknown>)?.pageSize === 1,
    );
    expect(pageCall?.[1]).toMatchObject({ pageNo: 2, pageSize: 1 });
    expect(result.items).toHaveLength(1);
    expect(result.total).toBe(1616);
    expect(result.pageNo).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.pages).toBe(1616);
  });
});

describe("getModelByCode", () => {
  beforeEach(() => getMock.mockReset());

  it("详情 VO → ModelRecord（suppliers/params/routeRefs 组装）", async () => {
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/models/by-code/qwen3.5-flash") return Promise.resolve(DETAIL_VO);
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const record = await getModelByCode("qwen3.5-flash");

    expect(record).not.toBeNull();
    expect(record!.typeLabel).toBe("大语言模型 (LLM)");
    expect(record!.modalityPreview).toBe("T+I → T");
    expect(record!.inputModalitiesText).toBe("text · image");
    expect(record!.tagsText).toBe("general · low-cost");
    expect(record!.params.temperature).toBe("0.7");
    expect(record!.params.topK).toBe("—");
    expect(record!.params.capabilities).toEqual(["chat", "vision"]);
    expect(record!.suppliers[0]).toMatchObject({
      providerCode: "dashscope",
      providerName: "阿里云百炼",
      providerModelId: "qwen3.5-flash",
      weight: 100,
      billingRate: 1.2,
      status: "enabled",
      keyConfigured: true,
    });
    expect(record!.providerSummary).toBe("阿里云百炼（主） · 共 1 个供应商模型");
    expect(record!.primaryUpstreamId).toBe("qwen3.5-flash");
    expect(record!.supplierWeightTotal).toBe("100");
    expect(record!.routeRefs[0]).toEqual({
      name: "通用对话路由",
      alias: "general",
      type: "单模型",
      weight: 100,
      status: "启用",
      health: "HEALTHY",
    });
    expect(record!.priceVersion).toBe("v2026-09");
    expect(record!.shortDate).toBe("09-08");
    expect(record!.operator).toBe("lisi");
  });

  it("extraBindings 合并为多个供应商", async () => {
    const withExtra = {
      ...DETAIL_VO,
      extraBindings: JSON.stringify([
        {
          providerId: "pv-2",
          providerModelId: "qwen3.5-flash-backup",
          weight: 0,
          costMultiplier: 1.1,
          referenceMultiplier: 1.3,
          enabled: false,
        },
      ]),
    };
    getMock.mockImplementation((path: string) => {
      if (path?.startsWith("/api/nexmux/models/by-code/")) return Promise.resolve(withExtra);
      if (path === "/api/nexmux/providers") {
        return Promise.resolve({
          records: [
            PROVIDER_VO,
            { providerId: "pv-2", providerCode: "openai", displayName: "OpenAI", vendorCode: "openai", keyStatus: "none" },
          ],
        });
      }
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [] });
      return Promise.resolve({ records: [] });
    });

    const record = await getModelByCode("qwen3.5-flash");

    expect(record!.suppliers).toHaveLength(2);
    expect(record!.suppliers[1]).toMatchObject({
      providerCode: "openai",
      providerModelId: "qwen3.5-flash-backup",
      status: "disabled",
      keyConfigured: false,
      costRate: 1.1,
    });
    expect(record!.routeSummary).toBe("未关联路由");
  });
});describe("listModels", () => {
  beforeEach(() => getMock.mockReset());

  it("组装 items/counts/filters（卡片派生字段齐全）", async () => {
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/models") {
        return Promise.resolve({ records: [MODEL_VO], total: 1, pages: 1 });
      }
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const result = await listModels({ type: "all" });

    // total 为筛选后总数（后端 PageResult.total）
    expect(result.total).toBe(1);
    expect(result.pageNo).toBe(1);
    expect(result.pageSize).toBe(50);
    expect(result.pages).toBe(1);
    const item = result.items[0];
    expect(item.code).toBe("qwen3.5-flash");
    expect(item.status).toBe("enabled");
    expect(item.type).toBe("LLM");
    expect(item.providerIcon).toBe("cloud");
    expect(item.specSummary).toBe("256K · 32K");
    expect(item.pricingSummary).toBe("$0.2 / $1.6 /M");
    expect(item.tierLabel).toBe("");
    expect(item.routes).toEqual({ total: 1, active: 1 });
    expect(item.routeSummary).toBe("1 条路由 · 1 条活跃");
    expect(item.suppliers).toHaveLength(1);
    expect(result.filters.type.map((t: { value: string }) => t.value)).toEqual([
      "all", "LLM", "Image", "Audio", "Embedding",
    ]);
    expect(result.filters.providers[0].text).toBe("全部 1");
    expect(result.filters.providers[1]).toEqual({ value: "dashscope", text: "阿里云百炼 1" });
  });

  it("分页透传：page/pageSize 下发后端，total/pages 取自 PageResult", async () => {
    getMock.mockImplementation((path: string, params?: Record<string, unknown>) => {
      if (path === "/api/nexmux/models" && params?.pageNo === 2) {
        return Promise.resolve({ records: [MODEL_VO], total: 1616, pages: 1616 });
      }
      if (path === "/api/nexmux/models") {
        return Promise.resolve({ records: [MODEL_VO], total: 1616, pages: 1616 });
      }
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const result = await listModels({ type: "all", page: 2, pageSize: 1 });

    // fetchAll（counts 全量）由 mock 固定 pageSize 500，分页 items 请求 pageSize 1
    const pageCall = getMock.mock.calls.find(
      ([path, params]) =>
        path === "/api/nexmux/models" &&
        (params as Record<string, unknown>)?.pageSize === 1,
    );
    expect(pageCall?.[1]).toMatchObject({ pageNo: 2, pageSize: 1 });
    expect(result.items).toHaveLength(1);
    expect(result.total).toBe(1616);
    expect(result.pageNo).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.pages).toBe(1616);
  });
});

describe("getModelByCode", () => {
  beforeEach(() => getMock.mockReset());

  it("详情 VO → ModelRecord（suppliers/params/routeRefs 组装）", async () => {
    getMock.mockImplementation((path: string) => {
      if (path === "/api/nexmux/models/by-code/qwen3.5-flash") return Promise.resolve(DETAIL_VO);
      if (path === "/api/nexmux/providers") return Promise.resolve({ records: [PROVIDER_VO] });
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [ROUTE_VO] });
      return Promise.resolve({ records: [] });
    });

    const record = await getModelByCode("qwen3.5-flash");

    expect(record).not.toBeNull();
    expect(record!.typeLabel).toBe("大语言模型 (LLM)");
    expect(record!.modalityPreview).toBe("T+I → T");
    expect(record!.inputModalitiesText).toBe("text · image");
    expect(record!.tagsText).toBe("general · low-cost");
    expect(record!.params.temperature).toBe("0.7");
    expect(record!.params.topK).toBe("—");
    expect(record!.params.capabilities).toEqual(["chat", "vision"]);
    expect(record!.suppliers[0]).toMatchObject({
      providerCode: "dashscope",
      providerName: "阿里云百炼",
      providerModelId: "qwen3.5-flash",
      weight: 100,
      billingRate: 1.2,
      status: "enabled",
      keyConfigured: true,
    });
    expect(record!.providerSummary).toBe("阿里云百炼（主） · 共 1 个供应商模型");
    expect(record!.primaryUpstreamId).toBe("qwen3.5-flash");
    expect(record!.supplierWeightTotal).toBe("100");
    expect(record!.routeRefs[0]).toEqual({
      name: "通用对话路由",
      alias: "general",
      type: "单模型",
      weight: 100,
      status: "启用",
      health: "HEALTHY",
    });
    expect(record!.priceVersion).toBe("v2026-09");
    expect(record!.shortDate).toBe("09-08");
    expect(record!.operator).toBe("lisi");
  });

  it("extraBindings 合并为多个供应商", async () => {
    const withExtra = {
      ...DETAIL_VO,
      extraBindings: JSON.stringify([
        {
          providerId: "pv-2",
          providerModelId: "qwen3.5-flash-backup",
          weight: 0,
          costMultiplier: 1.1,
          referenceMultiplier: 1.3,
          enabled: false,
        },
      ]),
    };
    getMock.mockImplementation((path: string) => {
      if (path?.startsWith("/api/nexmux/models/by-code/")) return Promise.resolve(withExtra);
      if (path === "/api/nexmux/providers") {
        return Promise.resolve({
          records: [
            PROVIDER_VO,
            { providerId: "pv-2", providerCode: "openai", displayName: "OpenAI", vendorCode: "openai", keyStatus: "none" },
          ],
        });
      }
      if (path === "/api/nexmux/routes") return Promise.resolve({ records: [] });
      return Promise.resolve({ records: [] });
    });

    const record = await getModelByCode("qwen3.5-flash");

    expect(record!.suppliers).toHaveLength(2);
    expect(record!.suppliers[1]).toMatchObject({
      providerCode: "openai",
      providerModelId: "qwen3.5-flash-backup",
      status: "disabled",
      keyConfigured: false,
      costRate: 1.1,
    });
    expect(record!.routeSummary).toBe("未关联路由");
  });
});

describe("createModel", () => {
  beforeEach(() => {
    postMock.mockReset();
    getMock.mockReset();
  });

  it("契约字段白名单 + CSV 拆数组 + status=enabled 补 enable", async () => {
    postMock.mockResolvedValueOnce({ ...MODEL_VO, status: "DRAFT" });
    // enable 路径：modelIdOf 的 by-code 查询 + enable 调用
    getMock.mockResolvedValue({ modelId: "md-1" });
    postMock.mockResolvedValueOnce({ ...MODEL_VO, status: "ENABLED" });

    await createModel({
      modelCode: "qwen3.5-flash",
      displayName: "Qwen3.5 Flash",
      modelType: "LLM",
      primaryProviderCode: "dashscope",
      primaryProviderModelId: "qwen3.5-flash",
      contextLength: 256000,
      inputModalities: "text, image",
      tags: "free, lite",
      status: "enabled",
      // 契约外字段（默认参数/定价等）
      temperature: 0.7,
      releaseDate: "2026-01-01",
    });

    const createPayload = postMock.mock.calls[0][1] as Record<string, unknown>;
    expect(postMock.mock.calls[0][0]).toBe("/api/nexmux/models");
    expect(createPayload.inputModalities).toEqual(["text", "image"]);
    expect(createPayload.tags).toEqual(["free", "lite"]);
    expect(createPayload).not.toHaveProperty("temperature");
    expect(createPayload).not.toHaveProperty("status");
    // status=enabled → 创建后补 enable
    expect(postMock.mock.calls[1][0]).toBe("/api/nexmux/models/md-1/enable");
  });

  it("status 非 enabled 时不补 enable", async () => {
    postMock.mockResolvedValue({ ...MODEL_VO, status: "DRAFT" });

    await createModel({ modelCode: "m", displayName: "M", primaryProviderCode: "dashscope" });

    expect(postMock).toHaveBeenCalledTimes(1);
  });
});

describe("updateModel", () => {
  beforeEach(() => {
    getMock.mockReset();
    putMock.mockReset();
  });

  it("只转发 ModelUpdateRequest 契约字段", async () => {
    getMock.mockResolvedValue({ modelId: "md-1" });
    putMock.mockResolvedValue(MODEL_VO);

    await updateModel("qwen3.5-flash", {
      displayName: "新名字",
      description: "desc",
      contextLength: 128000,
      inputModalities: "text",
      status: "disabled",
      apiKey: "x",
    });

    expect(putMock).toHaveBeenCalledWith("/api/nexmux/models/md-1", {
      displayName: "新名字",
      description: "desc",
      contextLength: 128000,
      maxOutputTokens: undefined,
      inputModalities: ["text"],
      outputModalities: undefined,
      tags: undefined,
    });
  });
});

describe("invokeModelTest", () => {
  it("按 code 解析 id 后调用 invoke-test", async () => {
    getMock.mockResolvedValue({ modelId: "md-1" });
    postMock.mockResolvedValue(true);

    const ok = await invokeModelTest("qwen3.5-flash");

    expect(ok).toBe(true);
    expect(postMock).toHaveBeenCalledWith("/api/nexmux/models/md-1/invoke-test");
  });
});
