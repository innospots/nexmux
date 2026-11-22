import { getAssembly } from "@pactor-app/shell/server";

/** globalPageId 形如 <moduleId>:<pageId> */
function moduleBasePath(globalPageId: string): string {
  const moduleId = globalPageId.split(":")[0];
  if (!moduleId || moduleId === "shell") {
    return "";
  }
  const module = getAssembly().modules.find((item) => item.id === moduleId);
  const basePath = module?.basePath?.trim() ?? "";
  return basePath === "/" ? "" : basePath;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/**
 * 模块内链接补 Shell 挂载前缀。
 *
 * 模块打包器只重写 `navigate` / `app.reload` 的 `params.to`，YAML 里的
 * `href`（面包屑、表格链接等）仍是模块内相对路径（如 `/providers`），
 * 在 Shell 下会跳到 404。装配器已把页面 path 拼上 basePath，这里按同一
 * 前缀补齐 `href`；`/api/`、外链与已带前缀的值原样保留。
 */
export function prefixModuleLinks(schema: unknown, globalPageId: string): unknown {
  const basePath = moduleBasePath(globalPageId);
  if (basePath === "") {
    return schema;
  }

  const rewriteHref = (href: string): string => {
    if (!href.startsWith("/") || href.startsWith("//") || href.startsWith("/api/")) {
      return href;
    }
    if (href === basePath || href.startsWith(`${basePath}/`)) {
      return href;
    }
    return `${basePath}${href}`;
  };

  const walk = (node: unknown): unknown => {
    if (Array.isArray(node)) {
      return node.map(walk);
    }
    if (!isRecord(node)) {
      return node;
    }
    const next: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(node)) {
      next[key] = key === "href" && typeof value === "string" ? rewriteHref(value) : walk(value);
    }
    return next;
  };

  return walk(schema);
}
