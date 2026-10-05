/**
 * 日志/成本/审计 adapter 共用的格式化工具（与对应 mock 逐字对齐）。
 */

/** ISO → "yyyy-MM-dd HH:mm:ss" */
export function fullTime(iso?: string): string {
  return iso ? iso.slice(0, 19).replace("T", " ") : "";
}

/** ISO → "MM-DD HH:mm:ss" */
export function shortTime(iso?: string): string {
  return iso ? iso.slice(5, 19).replace("T", " ") : "";
}

/** ISO → "MM-DD HH:mm" */
export function minuteTime(iso?: string): string {
  return iso ? iso.slice(5, 16).replace("T", " ") : "";
}

export function thousands(n: number): string {
  return n.toLocaleString("en-US");
}

export function compactTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
  return String(n);
}

export function parseJsonSafe<T>(text: string | null | undefined, fallback: T): T {
  if (!text) return fallback;
  try {
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

/** pretty JSON（diff/详情展示用；非法原文返回） */
export function prettyJson(text: string | null | undefined): string | null {
  if (text == null) return null;
  const parsed = parseJsonSafe<unknown>(text, undefined);
  return parsed === undefined ? text : JSON.stringify(parsed, null, 2);
}
