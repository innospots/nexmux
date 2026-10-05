/**
 * call-log / cost / audit adapter 黄金测试。
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

import { getCallLogById, listCallLogs } from "./call-log-adapter";
import { getCostById } from "./cost-adapter";
import { getAuditById, listAudits } from "./audit-adapter";

const LOG_VO = {
  requestId: "req-9f2a1c4d",
  clientId: "cl-1",
  routeId: "rt-1",
  modelCode: "openai/o4-mini",
  status: "SUCCESS",
  latencyMs: 320,
  requestedAt: "2026-09-18T14:32:18",
  appKeyId: "gw-ak-7f3a2b1c",
  userEmail: "dev@example.com",
  providerCode: "openai",
  protocol: "OpenAI / Chat Completions",
  routeGroup: "默认分组",
  application: "mobile-app",
  alias: "general",
  httpStatus: 200,
  inputTokens: 2100,
  outputTokens: 1800,
  cacheReadTokens: 0,
  cacheWriteTokens: 0,
  costUsd: 0.0106,
  billingUsd: 0.0127,
};

const LOG_DETAIL = {
  ...LOG_VO,
  ttftMs: 180,
  upstreamModel: "o4-mini",
  upstreamEndpoint: "https://api.openai.com/v1",
  upstreamStatus: "200",
  upstreamLatencyMs: 295,
  retries: 0,
  clientIp: "10.0.0.8",
  ingressMethod: "POST",
  ingressPath: "/v1/chat/completions",
  traceId: "trace-1c4d",
  routeTrace: '[{"name":"match","input":"alias=general","decision":"命中","output":"rt-1","duration":"1ms","reason":"别名匹配"}]',
  pricingSnapshot: '{"priceVersion":"pricing@v7","priceEffective":"2026-09-01","costRate":1.0,"billingRate":1.2,"multiplier":1.0}',
  promptSnapshot: '{"policy":"full","policyNote":"全量留存","prompt":"hi","response":"hello"}',
};

const AUDIT_VO = {
  auditId: "au-1",
  resourceType: "ROUTE",
  resourceId: "rt-1",
  operation: "PUBLISH",
  changeSummary: "首次发布",
  changeDiff: '{"before":null,"after":{"alias":"general","status":"PUBLISHED"}}',
  operatorId: "zhangsan",
  clientIp: "10.0.0.8",
  requestId: "req-audit-1",
  userAgent: "nexmux-console/1.0",
  createdAt: "2026-09-18T14:30:00",
};

function stubAll() {
  getMock.mockImplementation((path: string) => {
    if (path === "/api/nexmux/clients") {
      return Promise.resolve({ records: [{ clientId: "cl-1", displayName: "移动 App" }] });
    }
    if (path === "/api/nexmux/routes") {
      return Promise.resolve({
        records: [{ routeId: "rt-1", routeCode: "general-chat", displayName: "通用对话路由", routeSpec: { strategyType: "FIXED" } }],
      });
    }
    if (path === "/api/nexmux/models") {
      return Promise.resolve({ records: [{ modelCode: "openai/o4-mini", displayName: "o4-mini" }] });
    }
    if (path === "/api/nexmux/providers") {
      return Promise.resolve({ records: [{ providerCode: "openai", displayName: "OpenAI" }] });
    }
    if (path === "/api/nexmux/request-logs") return Promise.resolve({ records: [LOG_VO], total: 1 });
    if (path === "/api/nexmux/request-logs/req-9f2a1c4d") return Promise.resolve(LOG_DETAIL);
    if (path === "/api/nexmux/costs/req-9f2a1c4d") {
      return Promise.resolve({
        ...LOG_VO,
        pricingSnapshot: '{"currency":"USD","priceVersion":"pricing@v7","priceEffective":"2026-09-01","tierUpto":null,"tierInputPrice":1.1,"tierOutputPrice":4.4,"tierCacheReadPrice":null,"multiplier":1.0}',
      });
    }
    if (path === "/api/nexmux/audit-logs") return Promise.resolve({ records: [AUDIT_VO], total: 1 });
    if (path === "/api/nexmux/audit-logs/au-1") return Promise.resolve(AUDIT_VO);
    return Promise.resolve({ records: [] });
  });
}

describe("call-log-adapter", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("列表：usageText/costText/join 名称", async () => {
    const result = await listCallLogs({ status: "all" });

    expect(result.filteredText).toBe("1");
    const item = result.items[0];
    expect(item.statusLabel).toBe("成功");
    expect(item.timeUtc).toBe("2026-09-18 14:32:18 UTC");
    expect(item.timeShort).toBe("09-18 14:32:18 UTC");
    expect(item.latencyText).toBe("320ms");
    expect(item.usageText).toBe("2.1K/1.8K");
    expect(item.totalTokensText).toBe("3,900");
    expect(item.costText).toBe("0.0106");
    expect(item.marginText).toBe("0.0021");
    expect(item.clientName).toBe("移动 App");
    expect(item.routeName).toBe("通用对话路由");
    expect(item.modelName).toBe("o4-mini");
    expect(item.providerName).toBe("OpenAI");
    expect(item.strategyText).toBe("固定");
    expect(item.authResult).toBe("通过（AppKey 有效）");
  });

  it("详情：trace/pricing/prompt 快照展开", async () => {
    const record = await getCallLogById("req-9f2a1c4d");

    expect(record!.trace).toHaveLength(1);
    expect(record!.trace[0].name).toBe("match");
    expect(record!.pricingSnapshot).toBe("pricing@v7（2026-09-01 生效） · C=1 · M=1.2 · 无时段倍率");
    expect(record!.pr).toEqual({ policy: "full", policyNote: "全量留存", prompt: "hi", response: "hello" });
    expect(record!.ttftText).toBe("180ms");
    expect(record!.upstreamLatencyText).toBe("295ms");
  });
});

describe("cost-adapter", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("详情：档位/倍率/计算过程文案", async () => {
    const record = await getCostById("req-9f2a1c4d");

    expect(record!.tierLabel).toBe("∞");
    expect(record!.tierDetail).toBe("UPTO ∞（无上限档）");
    expect(record!.multiplierText).toBe("1x");
    expect(record!.inputCalcText).toBe("2,100 × $1.1 × 1 ÷ 1M = $0.0023");
    expect(record!.outputCalcText).toBe("1,800 × $4.4 × 1 ÷ 1M = $0.0079");
    expect(record!.cacheCalcText).toBe("无缓存用量");
    expect(record!.tierPriceText).toBe("输入 $1.1/M · 输出 $4.4/M · 缓存读 —");
    expect(record!.contextSummary).toBe("09-18 14:32 · 移动 App（gw-ak-7f3a2b1c）· o4-mini · OpenAI");
  });
});

describe("audit-adapter", () => {
  beforeEach(() => {
    getMock.mockReset();
    stubAll();
  });

  it("列表：operation/resourceType 展示名映射", async () => {
    const result = await listAudits({});

    const item = result.items[0];
    expect(item.operation).toBe("发布");
    expect(item.operationType).toBe("publish");
    expect(item.resourceType).toBe("Route");
    expect(item.resourceTypeKey).toBe("route");
    expect(item.timeUtc).toBe("2026-09-18 14:30:00 (UTC+8)");
    expect(item.timeShort).toBe("09-18 14:30");
    expect(result.filteredText).toBe("1");
  });

  it("详情：信封快照 + LCS diffLines + context", async () => {
    const record = await getAuditById("au-1");

    expect(record!.beforeJson).toBeNull();
    expect(record!.afterJson).toContain("general");
    expect(record!.diffLines.every((l) => l.type === "add")).toBe(true);
    expect(record!.context).toEqual([
      { label: "操作 IP", value: "10.0.0.8" },
      { label: "请求 ID", value: "req-audit-1" },
      { label: "终端", value: "nexmux-console/1.0" },
      { label: "变更摘要", value: "首次发布" },
    ]);
  });
});
