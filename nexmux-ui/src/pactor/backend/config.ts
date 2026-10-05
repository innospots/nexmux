/**
 * BFF 后端连接配置（服务端 only，勿 import 进客户端组件）。
 *
 * - NEXMUX_BACKEND_BASE_URL：nexmux-spring 地址（缺省 http://localhost:8082）；
 *   显式设为 "mock" 时强制走本地 mock（演示模式）。
 * - NEXMUX_BACKEND_TOKEN：Nexus Console 访问令牌（Bearer）；
 *   未配置时依赖后端 dev-session（nexus.console.web.security.enabled=false）。
 *
 * 设计依据：docs/design/nexmux-ui-data-api-design.md §7.2（Q3-A 服务账号凭证）。
 */

export interface BackendConfig {
  baseUrl: string;
  token: string | undefined;
  /** false = 强制 mock 模式 */
  enabled: boolean;
}

export function backendConfig(): BackendConfig {
  const raw = process.env.NEXMUX_BACKEND_BASE_URL ?? "http://localhost:8082";
  return {
    baseUrl: raw.replace(/\/+$/, ""),
    token: process.env.NEXMUX_BACKEND_TOKEN || undefined,
    enabled: raw.toLowerCase() !== "mock",
  };
}
