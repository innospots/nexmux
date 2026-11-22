const PREFIX_KEY = "__GENERAL_APP_API_PREFIX";

type GlobalWithPrefix = typeof globalThis & { [PREFIX_KEY]?: string };

export function installModuleApiPrefix(prefix: string): void {
  (globalThis as GlobalWithPrefix)[PREFIX_KEY] = prefix.replace(/\/$/, "");
}

/** 独立运行保持 /api/general-app；模块包改写到 /api/modules/<id>/general-app。 */
export function apiUrl(path: string): string {
  const prefix = (globalThis as GlobalWithPrefix)[PREFIX_KEY];
  if (!prefix || !path.startsWith("/api/")) return path;
  if (path === prefix || path.startsWith(`${prefix}/`)) return path;
  return `${prefix}${path.slice("/api".length)}`;
}
