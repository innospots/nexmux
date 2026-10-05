/**
 * services-real 写通道测试：PUT providers 字段拆分（设计 D2）与桩映射表（设计 D4）。
 */

import { beforeEach, describe, expect, it, vi } from "vitest";

const updateProviderMock = vi.fn();
const setProviderEnabledMock = vi.fn();

vi.mock("./adapters/provider-adapter", () => ({
  createProvider: vi.fn(),
  getProviderByCode: vi.fn(),
  listProviders: vi.fn(),
  pullProviderModels: vi.fn(),
  setProviderEnabled: (...args: unknown[]) => setProviderEnabledMock(...args),
  testProviderConnection: vi.fn(),
  updateProvider: (...args: unknown[]) => updateProviderMock(...args),
}));

import { pactorMutations, stubMutations } from "./services-real";

describe("PUT providers 字段拆分", () => {
  beforeEach(() => {
    updateProviderMock.mockReset();
    setProviderEnabledMock.mockReset();
  });

  it("契约字段转发 updateProvider，status 变化触发 disable", async () => {
    updateProviderMock.mockResolvedValue({ status: "ENABLED" });
    setProviderEnabledMock.mockResolvedValue({ status: "DISABLED" });

    await pactorMutations["PUT providers"]({
      id: "zai",
      body: {
        displayName: "Z.AI 新",
        baseUrl: "https://api.z.ai/v1",
        region: "",
        timeoutMs: 30000,
        status: "disabled",
        description: "落桩字段",
        apiKey: "sk-secret",
      },
    });

    expect(updateProviderMock).toHaveBeenCalledWith("zai", {
      displayName: "Z.AI 新",
      baseUrl: "https://api.z.ai/v1",
      region: "",
      timeoutMs: 30000,
    });
    expect(setProviderEnabledMock).toHaveBeenCalledWith("zai", false);
  });

  it("status 未变化时不调用 enable/disable", async () => {
    updateProviderMock.mockResolvedValue({ status: "ENABLED" });

    await pactorMutations["PUT providers"]({
      id: "zai",
      body: { displayName: "Z.AI", status: "enabled" },
    });

    expect(updateProviderMock).toHaveBeenCalledWith("zai", { displayName: "Z.AI" });
    expect(setProviderEnabledMock).not.toHaveBeenCalled();
  });

  it("非法 timeoutMs 不进入更新体", async () => {
    updateProviderMock.mockResolvedValue({ status: "ENABLED" });

    await pactorMutations["PUT providers"]({
      id: "zai",
      body: { displayName: "Z.AI", timeoutMs: "abc" },
    });

    expect(updateProviderMock).toHaveBeenCalledWith("zai", { displayName: "Z.AI" });
  });
});

describe("stubMutations 桩映射表", () => {
  it("DELETE providers 默认成功并标注 stub", async () => {
    const result = await stubMutations["DELETE providers"]({ id: "zai", body: {} });
    expect(result).toEqual({ stub: true, ok: true });
  });
});

const updateModelMock = vi.fn();
const setModelEnabledMock = vi.fn();

vi.mock("./adapters/model-adapter", () => ({
  createModel: vi.fn(),
  getModelByCode: vi.fn(),
  invokeModelTest: vi.fn(),
  listModels: vi.fn(),
  setModelEnabled: (...args: unknown[]) => setModelEnabledMock(...args),
  updateModel: (...args: unknown[]) => updateModelMock(...args),
}));

describe("PUT models 字段拆分", () => {
  beforeEach(() => {
    updateModelMock.mockReset();
    setModelEnabledMock.mockReset();
  });

  it("body 整体转 updateModel（适配器白名单），status 变化触发 disable", async () => {
    updateModelMock.mockResolvedValue({ status: "ENABLED" });
    setModelEnabledMock.mockResolvedValue({ status: "DISABLED" });

    const body = { displayName: "新名字", status: "disabled", temperature: 0.7 };
    await pactorMutations["PUT models"]({ id: "openai/o4-mini", body });

    expect(updateModelMock).toHaveBeenCalledWith("openai/o4-mini", body);
    expect(setModelEnabledMock).toHaveBeenCalledWith("openai/o4-mini", false);
  });

  it("status 未变化时不调用 enable/disable", async () => {
    updateModelMock.mockResolvedValue({ status: "DRAFT" });

    await pactorMutations["PUT models"]({ id: "m", body: { displayName: "x", status: "disabled" } });

    expect(setModelEnabledMock).not.toHaveBeenCalled();
  });
});
