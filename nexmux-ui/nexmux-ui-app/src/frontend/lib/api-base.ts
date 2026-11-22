const PREFIX_KEY = "__NEXMUX_API_PREFIX";

type GlobalWithPrefix = typeof globalThis & { [PREFIX_KEY]?: string };

/**
 * 模块 client 入口安装 Shell 挂载前缀。独立运行不调用，逻辑路径原样请求。
 */
export function installModuleApiPrefix(prefix: string): void {
  (globalThis as GlobalWithPrefix)[PREFIX_KEY] = prefix.replace(/\/$/, "");
}

/**
 * 把逻辑 API 路径改成当前运行模式的公开 URL。
 *
 * 独立运行：`/api/d/models` 保持不变。
 * 模块包：与 `pactor module build` 一致，改成
 * `/api/modules/<id>/d/models`（去掉 `/api` 前缀再接到模块挂载点后）。
 * 已经是挂载路径的 URL 不再改写。
 */
export function apiUrl(path: string): string {
  const prefix = (globalThis as GlobalWithPrefix)[PREFIX_KEY];
  if (!prefix || !path.startsWith("/api/")) {
    return path;
  }
  if (path === prefix || path.startsWith(`${prefix}/`)) {
    return path;
  }
  return `${prefix}${path.slice("/api".length)}`;
}
