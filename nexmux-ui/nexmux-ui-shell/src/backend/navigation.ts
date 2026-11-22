import { getPagesIndex } from "@pactor-app/shell/server";

const BACKEND = process.env.NEXMUX_API_BASE_URL ?? "http://localhost:8082";

/** 一级导航 icon 别名（对齐菜单语义，兼容 @pactor-app/shell 内建 Lucide 映射） */
const TOP_LEVEL_RAIL_ICON: Record<string, string> = {
  dashboard: "layout-dashboard",
  assets: "boxes",
  access: "users",
  operations: "activity",
  debug: "terminal",
  system: "settings",
};

function resolveTopLevelIcon(menuKey: string, icon?: string | null): string | undefined {
  const key = menuKey.trim();
  const preferred = TOP_LEVEL_RAIL_ICON[key];
  const raw = icon?.trim();
  return preferred ?? (raw || undefined);
}

type NavigationMenu = {
  menuKey: string;
  menuName: string;
  pageKey?: string | null;
  routePath?: string | null;
  externalUrl?: string | null;
  icon?: string | null;
  openMode?: string | null;
  children?: NavigationMenu[];
};

type ShellMenu = {
  id: string;
  title: string;
  type: "group" | "page" | "link";
  icon?: string;
  pageId?: string;
  path?: string;
  external?: boolean;
  target?: string;
  hidden?: boolean;
  activeMenu?: string;
  children?: ShellMenu[];
};

/** 已废弃的宿主页面：不再作为路由与菜单下发 */
const RETIRED_PAGE_IDS = new Set([
  "pactor-app-general-app:workspace",
  "pactor-app-general-app:dashboard",
]);

/**
 * 后端只下发可见导航，详情 / 新建 / 编辑等页面没有菜单项，路由会 404。
 * 按装配后的页面索引为未覆盖路径补 hidden 菜单（不进入导航，仅承载路由）。
 */
export function mergeHiddenRouteMenus(menus: ShellMenu[]): ShellMenu[] {
  const covered = new Set<string>();
  const collectPaths = (items: ShellMenu[]) => {
    for (const item of items) {
      if (item.path) {
        covered.add(item.path);
      }
      if (item.children) {
        collectPaths(item.children);
      }
    }
  };
  collectPaths(menus);

  const hidden: ShellMenu[] = [];
  for (const page of getPagesIndex().pages) {
    if (covered.has(page.path) || RETIRED_PAGE_IDS.has(page.globalPageId)) {
      continue;
    }
    hidden.push({
      id: page.globalPageId,
      type: "page",
      title: page.title ?? page.pageId,
      pageId: page.globalPageId,
      path: page.path,
      hidden: true,
    });
  }
  return [...menus, ...hidden];
}

/** 用平台登录态拉取后端导航，并按已装配页面补上公开路径。 */
export async function loadBackendMenus(accessToken: string): Promise<ShellMenu[] | null> {
  let response: Response;
  try {
    response = await fetch(`${BACKEND}/api/d/platform/navigation/menus`, {
      headers: { authorization: `Bearer ${accessToken}` },
      cache: "no-store",
    });
  } catch {
    return null;
  }
  if (!response.ok) return null;
  const envelope = (await response.json()) as { success?: boolean; data?: unknown };
  if (envelope.success === false || !Array.isArray(envelope.data)) return null;
  const pages = new Map(getPagesIndex().pages.map((page) => [page.globalPageId, page.path]));
  return convertNavigation(envelope.data as NavigationMenu[], pages);
}

function convertNavigation(
  items: NavigationMenu[],
  pages: Map<string, string>,
  depth = 0,
): ShellMenu[] {
  return items.flatMap((item): ShellMenu[] => {
    const children = convertNavigation(item.children ?? [], pages, depth + 1);
    const icon =
      depth === 0
        ? resolveTopLevelIcon(item.menuKey, item.icon)
        : item.icon?.trim() || undefined;
    const base = {
      id: item.menuKey,
      title: item.menuName,
      ...(icon ? { icon } : {}),
    };
    if (children.length > 0) {
      return [{ ...base, type: "group" as const, children }];
    }
    const pageId = item.pageKey?.trim() ?? "";
    const assembledPath = pageId ? pages.get(pageId) : undefined;
    if (pageId && assembledPath) {
      return [{
        ...base,
        type: "page" as const,
        pageId,
        path: item.routePath?.trim() || assembledPath,
      }];
    }
    if (item.externalUrl) {
      return [{
        ...base,
        type: "link" as const,
        path: item.externalUrl,
        external: true,
        ...(item.openMode === "NEW_WINDOW" ? { target: "_blank" } : {}),
      }];
    }
    return [];
  });
}
