/**
 * nexmux-spring HTTP 客户端：R<T> 解包、错误规范化、超时与 Bearer 注入。
 * 服务端 only（见 config.ts 注释）。
 *
 * 错误语义（设计 §8）：
 * - 后端 R.success=false → BackendError（携带后端 code/message）；
 * - 网络不可达 / 超时 → BackendError("NMX_BACKEND_UNAVAILABLE")，映射 HTTP 502；
 * - 后端 401/403（令牌失效）→ 原样抛出状态，由路由层透传。
 */

import { backendConfig } from "./config";

export class BackendError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly httpStatus: number = 502,
  ) {
    super(message);
    this.name = "BackendError";
  }
}

/** 平台统一响应包装（对齐 innospots-nexus-base R record） */
interface R<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

/** 平台分页结果（对齐 PageResult record） */
export interface PageResult<T> {
  records: T[];
  pageNo: number;
  pageSize: number;
  total: number;
  pages: number;
}

const DEFAULT_TIMEOUT_MS = 10_000;

async function request<T>(
  method: string,
  path: string,
  options: { params?: Record<string, unknown>; body?: unknown } = {},
): Promise<T> {
  const config = backendConfig();
  const url = new URL(config.baseUrl + path);
  for (const [key, value] of Object.entries(options.params ?? {})) {
    if (value === undefined || value === null || value === "") continue;
    url.searchParams.set(key, String(value));
  }

  const headers: Record<string, string> = { accept: "application/json" };
  if (options.body !== undefined) headers["content-type"] = "application/json";
  if (config.token) headers["authorization"] = `Bearer ${config.token}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      cache: "no-store",
      signal: AbortSignal.timeout(DEFAULT_TIMEOUT_MS),
    });
  } catch (error) {
    throw new BackendError(
      "NMX_BACKEND_UNAVAILABLE",
      `后端服务不可用: ${error instanceof Error ? error.message : String(error)}`,
      502,
    );
  }

  if (response.status === 401 || response.status === 403) {
    throw new BackendError("NMX_BACKEND_UNAUTHORIZED", "后端服务账号认证失败", response.status);
  }

  const payload = (await response.json().catch(() => null)) as R<T> | null;
  if (!payload || typeof payload.success !== "boolean") {
    throw new BackendError(
      "NMX_BACKEND_BAD_RESPONSE",
      `后端响应无法解析: HTTP ${response.status}`,
      502,
    );
  }
  if (!payload.success) {
    throw new BackendError(payload.code, payload.message || "后端调用失败", mapHttpStatus(payload.code));
  }
  return payload.data;
}

/** NMX 九字符码 → HTTP 状态（与 NexmuxStatusCode 映射一致；category 为第 4–5 位） */
function mapHttpStatus(code: string): number {
  const category = code.slice(3, 5);
  switch (category) {
    case "13":
      return 404; // RESOURCE_DATA（如 RESOURCE_NOT_FOUND）
    case "07":
      return 409; // DATA_CONSISTENCY（如 DUPLICATE_BUSINESS_KEY）
    case "02":
      // BUSINESS_RULE：ROUTE_NOT_PUBLISHED 为 503，其余 422
      return code === "NMX020002" ? 503 : 422;
    case "04":
      return 403; // PERMISSION_SECURITY
    default:
      return 400;
  }
}

export const backendClient = {
  get: <T>(path: string, params?: Record<string, unknown>) => request<T>("GET", path, { params }),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, { body }),
};

/** 全量拉取（catalog 种子即 1600+ 模型；BFF counts/filters 构成口径计算用，上限自保护） */
export async function fetchAll<T>(path: string, params?: Record<string, unknown>): Promise<T[]> {
  const page = await backendClient.get<PageResult<T>>(path, { pageNo: 1, pageSize: 5000, ...params });
  return page.records;
}
