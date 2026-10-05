/**
 * stats-adapter 黄金测试。
 */

import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();

vi.mock("../client", () => ({
  backendClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: vi.fn(),
    put: vi.fn(),
  },
  fetchAll: (path: string) => getMock(path, { pageNo: 1, pageSize: 5000 }).then(
    (page: { records: unknown[] }) => page.records,
  ),
  BackendError: class BackendError extends Error {},
}));

import {
  queryAvailabilityReal,
  queryClientUsageReal,
  queryDashboardReal,
  queryModelUsageReal,
} from "./stats-adapter";

const DASHBOARD_VO = {
  requests: 100,
  tokens: 390000,
  costUsd: 1.27,
  billedUsd: 1.52,
  successRate: 0.97,
  errorCount: 3,
  avgLatencyMs: 320.5,
  activeClients: 8,
  trend: [
    { t: "2026-09-18", requests: 60, tokens: 200000, cost: 0.7, avgLatencyMs: 300 },
    { t: "2026-09-19", requests: 40, tokens: 190000, cost: 0.57, avgLatencyMs: 340 },
  ],
  modelDistribution: [{ name: "openai/o4-mini", requests: 60 }],
  clientDistribution: [{ name: "cl-1", requests: 60 }],
  realtimeRpm: 128,
  realtimeTokens: 2100,
};

const USAGE_ROW = {
  key: "openai/o4-mini",
  requests: 100,
  errorCount: 3,
  inputTokens: 210000,
  outputTokens: 180000,
  cacheReadTokens: 50000,
  standardUsd: 1.06,
  billedUsd: 1.27,
  costUsd: 1.06,
  successRate: 0.97,
  avgLatencyMs: 320,
  avgTtftMs: 180,
  avgUpstreamMs: 295,
  failoverRate: 0.02,
  avgAttempts: 1.05,
};

const AVAILABILITY_VO = {
  providers: [{ ...USAGE_ROW, key: "openai" }],
  compare: [{ ...USAGE_ROW, key: "openai/o4-mini@openai" }],
  recentErrors: [
    {
      requestId: "req-3d8a9c07",
      requestedAt: "2026-09-17T22:41:55",
      modelCode: "openai/o4-mini",
      providerCode: "openai",
      errorCode: "UPSTREAM_502",
    },
  ],
};

function stubRelations() {
  getMock.mockImplementation((path: string) => {
    if (path === "/api/nexmux/models") {
      return Promise.resolve({ records: [{ modelCode: "openai/o4-mini", displayName: "o4-mini" }] });
    }
    if (path === "/api/nexmux/providers") {
      return Promise.resolve({ records: [{ providerCode: "openai", displayName: "OpenAI" }] });
    }
    if (path === "/api/nexmux/clients") {
      return Promise.resolve({
        records: [{
          clientId: "cl-1", clientCode: "mobile-app", displayName: "移动 App",
          appKeyId: "gw-ak-1",
          quotaSpec: '[{"quotaType":"lifetime","metric":"TOTAL_TOKEN","limit":1000000}]',
        }],
      });
    }
    if (path === "/api/nexmux/routes") {
      return Promise.resolve({
        records: [{ displayName: "通用对话路由", routeSpec: { candidates: [{ modelCode: "openai/o4-mini" }] } }],
      });
    }
    if (path === "/api/nexmux/stats/dashboard") return Promise.resolve(DASHBOARD_VO);
    if (path === "/api/nexmux/stats/model-usage") return Promise.resolve([USAGE_ROW]);
    if (path === "/api/nexmux/stats/client-usage") return Promise.resolve([{ ...USAGE_ROW, key: "cl-1" }]);
    if (path === "/api/nexmux/stats/provider-availability") return Promise.resolve(AVAILABILITY_VO);
    return Promise.resolve({ records: [] });
  });
}

describe("queryDashboardReal", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubRelations();
  });

  it("组装 stats/trend/分布/realtime", async () => {
    const d = await queryDashboardReal("2026-09-18", "2026-09-19");

    expect(d.stats.requests).toBe("100");
    expect(d.stats.tokens).toBe("0.4M");
    expect(d.stats.successRate).toBe("97.0");
    expect(d.stats.errorRate).toBe("3.00");
    expect(d.stats.failures).toBe("3");
    expect(d.stats.avgLatency).toBe(321);
    expect(d.stats.cost).toBe("$1.2700");
    expect(d.stats.avgCost).toBe("$0.0127");
    expect(d.stats.activeClients).toBe(8);
    expect(d.realtime).toEqual({ rpm: 128, tpm: "2.1K" });
    expect(d.trend[0]).toEqual({ t: "09-18", requests: 60, tokens: 0.2, cost: 0.7, latency: 300 });
    expect(d.models[0]).toEqual({ name: "o4-mini", percent: 100 });
    expect(d.clients[0]).toEqual({ name: "移动 App", percent: 100 });
    expect(d.metricKeys.requests).toEqual(["requests"]);
  });
});

describe("queryModelUsageReal / queryClientUsageReal", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubRelations();
  });

  it("模型用量行：全部展示文案 + 路由/模型名 join", async () => {
    const result = await queryModelUsageReal("2026-09-18", "2026-09-19");

    const row = result.items[0];
    expect(row.model).toBe("o4-mini");
    expect(row.route).toBe("通用对话路由");
    expect(row.requests).toBe("100");
    expect(row.inputRaw).toBe("210,000");
    expect(row.cacheHit).toBe("19.2%");
    expect(row.billed).toBe("$1.27");
    expect(row.avgBilled).toBe("$0.0127");
    expect(row.successRate).toBe("97.0%");
    expect(row.avgLatency).toBe("320ms");
    expect(row.ttft).toBe("180ms");
    expect(row.failoverRate).toBe("2.0%");
    expect(row.avgAttempts).toBe("1.05");
    expect(result.summary.billed).toBe("$1.27");
  });

  it("客户端用量行：quotaUsage 按 lifetime 规则计算", async () => {
    const result = await queryClientUsageReal("2026-09-18", "2026-09-19");

    const row = result.items[0];
    expect(row.name).toBe("移动 App");
    expect(row.appKeyId).toBe("gw-ak-1");
    expect(row.quotaUsage).toBe("390.0K/1.0M");
  });
});

describe("queryAvailabilityReal", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubRelations();
  });

  it("providers/compare/errors 三段组装", async () => {
    const result = await queryAvailabilityReal("2026-09-18", "2026-09-19");

    expect(result.providers[0]).toMatchObject({ provider: "OpenAI", errors: "3", successRate: "97.0%" });
    expect(result.compare[0]).toMatchObject({ model: "o4-mini", provider: "OpenAI" });
    expect(result.errors[0]).toEqual({
      id: "req-3d8a9c07",
      time: "09-17 22:41:55 UTC",
      target: "o4-mini / OpenAI",
      error: "UPSTREAM_502",
    });
  });
});
