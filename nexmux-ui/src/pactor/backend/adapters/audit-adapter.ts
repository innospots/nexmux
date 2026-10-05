/**
 * 审计适配器：AuditLogVo/DetailVo → UI 契约形状（AuditRecord）。
 *
 * - change_diff 支持信封格式 {"before":..., "after":...}（旧单快照视为 after）；
 * - diffLines 由 BFF 行级 diff 计算（LCS）；
 * - operation / resourceType 展示名映射对齐 audit-mock。
 */

import type {
  AuditContextRow,
  AuditDiffLine,
  AuditRecord,
} from "../../mocks/audit-mock";
import { backendClient, type PageResult } from "../client";
import { minuteTime, parseJsonSafe } from "./format-shared";
import type { AuditLogVo } from "../types";

// ── 常量（与 audit-mock 对齐；后端存储为大写操作码） ───────────

const OPERATION_LABELS: Record<string, string> = {
  CREATE: "创建",
  UPDATE: "修改",
  DELETE: "删除",
  ENABLE: "启用",
  DISABLE: "停用",
  PUBLISH: "发布",
  ROTATE: "Rotate Secret",
};

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  PROVIDER: "Provider",
  MODEL: "Model",
  ROUTE: "Route",
  CLIENT: "API Client",
  CLIENT_TEMPLATE: "Client Template",
};

// ── 行级 diff（LCS） ──────────────────────────────────────────

function diffLines(before: string | null, after: string | null): AuditDiffLine[] {
  const a = before?.split("\n") ?? [];
  const b = after?.split("\n") ?? [];
  const m = a.length;
  const n = b.length;
  // LCS 动态规划（审计 JSON 规模小，O(m*n) 可接受）
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const lines: AuditDiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (a[i] === b[j]) {
      lines.push({ type: "same", text: a[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      lines.push({ type: "remove", text: a[i] });
      i++;
    } else {
      lines.push({ type: "add", text: b[j] });
      j++;
    }
  }
  while (i < m) lines.push({ type: "remove", text: a[i++] });
  while (j < n) lines.push({ type: "add", text: b[j++] });
  return lines;
}

/** change_diff 信封解析：{before, after} 或旧单快照（视为 after） */
function splitSnapshots(changeDiff?: string): { before: unknown; after: unknown } {
  const parsed = parseJsonSafe<unknown>(changeDiff, null);
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)
      && ("before" in parsed || "after" in parsed)) {
    const envelope = parsed as { before?: unknown; after?: unknown };
    return { before: envelope.before ?? null, after: envelope.after ?? null };
  }
  return { before: null, after: parsed };
}

// ── VO → AuditRecord ──────────────────────────────────────────

function toRecord(vo: AuditLogVo): AuditRecord {
  const snapshots = splitSnapshots(vo.changeDiff);
  const beforeJson = snapshots.before == null ? null : JSON.stringify(snapshots.before, null, 2);
  const afterJson = snapshots.after == null ? null : JSON.stringify(snapshots.after, null, 2);
  const operation = vo.operation ?? "";
  const resourceType = vo.resourceType ?? "";

  const context: AuditContextRow[] = [
    { label: "操作 IP", value: vo.clientIp ?? "—" },
    { label: "请求 ID", value: vo.requestId ?? "—" },
    { label: "终端", value: vo.userAgent ?? "—" },
    { label: "变更摘要", value: vo.changeSummary ?? "—" },
  ];

  return {
    id: vo.auditId,
    date: vo.createdAt?.slice(0, 10) ?? "",
    timeUtc: `${vo.createdAt?.slice(0, 19).replace("T", " ") ?? ""} (UTC+8)`,
    timeShort: minuteTime(vo.createdAt),
    operator: vo.operatorId ?? "—",
    operation: OPERATION_LABELS[operation] ?? operation,
    operationType: operation.toLowerCase(),
    resourceType: RESOURCE_TYPE_LABELS[resourceType] ?? resourceType,
    resourceTypeKey: resourceType.toLowerCase(),
    resourceId: vo.resourceId ?? "—",
    ip: vo.clientIp ?? "—",
    requestId: vo.requestId ?? "—",
    userAgent: vo.userAgent ?? "—",
    beforeJson,
    afterJson,
    diffLines: diffLines(beforeJson, afterJson),
    context,
  };
}

// ── 对外服务 ───────────────────────────────────────────────────

export async function listAudits(params: {
  start?: string;
  end?: string;
  operator?: string;
  operation?: string;
  resourceType?: string;
  resourceId?: string;
}): Promise<{ items: AuditRecord[]; filteredText: string }> {
  const upper = (v?: string) => (v && v !== "all" ? v.toUpperCase() : undefined);
  const page = await backendClient.get<PageResult<AuditLogVo>>("/api/nexmux/audit-logs", {
    pageNo: 1,
    pageSize: 5000,
    start: params.start,
    end: params.end,
    operator: params.operator && params.operator !== "all" ? params.operator : undefined,
    operation: upper(params.operation),
    resourceType: upper(params.resourceType),
    resourceId: params.resourceId,
  });

  const items = page.records.map(toRecord);
  return { items, filteredText: items.length.toLocaleString("en-US") };
}

export async function getAuditById(id: string): Promise<AuditRecord | null> {
  const detail = await backendClient.get<AuditLogVo>(
    `/api/nexmux/audit-logs/${encodeURIComponent(id)}`,
  );
  return toRecord(detail);
}

export async function listAuditOperators(): Promise<string[]> {
  return backendClient.get<string[]>("/api/nexmux/audit-logs/operators");
}
