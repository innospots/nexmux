import { readFileSync, readdirSync, watch } from "node:fs";
import { join, relative } from "node:path";

import type { PageDsl } from "@pactor-app/core";
import { parseDocument } from "yaml";

import type { LayoutDsl, SitemapResource } from "pactor-app";

import type { DemoUser } from "@/backend/auth/accounts";
import { translate, type RequestI18n } from "@/backend/pages/i18n";

/** 页面清单与布局来自本地 YAML；可见导航来自后端，非菜单页面生成隐藏路由。 */

const YAML_DIR = join(process.cwd(), "src/backend/pages/yaml");

const ACTIVE_MENU: Record<string, string> = {
  "provider-create": "nav-providers",
  "provider-detail": "nav-providers",
  "provider-edit": "nav-providers",
  "model-create": "nav-models",
  "model-detail": "nav-models",
  "model-edit": "nav-models",
  "route-create": "nav-routes",
  "route-detail": "nav-routes",
  "route-edit": "nav-routes",
  "client-create": "nav-clients",
  "client-detail": "nav-clients",
  "client-edit": "nav-clients",
  "client-template-create": "nav-client-templates",
  "client-template-detail": "nav-client-templates",
  "client-template-edit": "nav-client-templates",
  "call-log-detail": "nav-call-logs",
  "cost-detail": "nav-costs",
  "audit-detail": "nav-audit",
};

function loadYaml<T>(file: string): T {
  const doc = parseDocument(readFileSync(join(YAML_DIR, file), "utf8"));
  if (doc.errors.length > 0) {
    throw new Error(
      `${file} 解析失败：${doc.errors.map((error) => error.message.split("\n")[0]).join("；")}`,
    );
  }
  return doc.toJS() as T;
}

interface PageEntry {
  id: string;
  path: string;
  title: string;
  version: number;
  public?: boolean;
  lazy?: boolean;
  layout?: string;
  activeMenu?: string;
}

interface SitemapFile {
  id: string;
  resourceType: "sitemap";
  version: number;
  dslVersion: string;
  app: { name: string };
  layout: string;
  auth: { loginPath: string };
  layouts: Record<string, LayoutDsl>;
  pages: PageEntry[];
}

interface YamlState {
  site: SitemapFile;
  pages: Map<string, { pageDsl: PageDsl }>;
}

function collectPageFiles(dir: string): Map<string, string> {
  const files = new Map<string, string>();
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const entryPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      for (const [id, file] of collectPageFiles(entryPath)) {
        if (files.has(id)) throw new Error(`页面 DSL id 重复：${id}`);
        files.set(id, file);
      }
      continue;
    }
    if (!entry.name.endsWith(".yaml") || entry.name === "sitemap.yaml") continue;
    files.set(entry.name.slice(0, -".yaml".length), relative(YAML_DIR, entryPath));
  }
  return files;
}

function loadState(): YamlState {
  const site = loadYaml<SitemapFile>("sitemap.yaml");
  const pageFiles = collectPageFiles(YAML_DIR);
  const pages = new Map<string, { pageDsl: PageDsl }>();
  for (const entry of site.pages) {
    const file = pageFiles.get(entry.id);
    if (!file) throw new Error(`页面 DSL 缺失：${entry.id}.yaml`);
    const pageDsl = loadYaml<PageDsl>(file);
    const page = pageDsl.page as unknown as Record<string, unknown>;
    const mode = page["access-mode"];
    if (page.id !== entry.id || !["public", "authenticated", "grant"].includes(String(mode))) {
      throw new Error(`页面 ${entry.id} 的 id 或 access-mode 无效`);
    }
    if (!entry.lazy) throw new Error(`页面 ${entry.id} 必须通过懒加载端点进行访问检查`);
    if (mode === "grant" && [page.domain, page.module, page.pageKey].some(
      (value) => typeof value !== "string" || value.trim() === "",
    )) {
      throw new Error(`页面 ${entry.id} 的 domain、module 或 pageKey 缺失`);
    }
    entry.public = mode === "public";
    pages.set(entry.id, { pageDsl });
  }
  return { site, pages };
}

let stateCache: YamlState | null = null;

function state(): YamlState {
  if (!stateCache) stateCache = loadState();
  return stateCache;
}

const globalForYamlWatch = globalThis as unknown as {
  __nexmuxYamlInvalidate?: () => void;
  __nexmuxYamlWatcher?: ReturnType<typeof watch>;
};
globalForYamlWatch.__nexmuxYamlInvalidate = () => {
  stateCache = null;
};
if (process.env.NODE_ENV !== "production" && !globalForYamlWatch.__nexmuxYamlWatcher) {
  globalForYamlWatch.__nexmuxYamlWatcher = watch(YAML_DIR, { recursive: true }, () => {
    globalForYamlWatch.__nexmuxYamlInvalidate?.();
  });
}

function localizeValue(value: unknown, messages: Readonly<Record<string, string>>): unknown {
  if (typeof value === "string") return translate(value, messages);
  if (Array.isArray(value)) return value.map((item) => localizeValue(item, messages));
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [key, localizeValue(child, messages)]),
    );
  }
  return value;
}

function resolveMenuTitle(
  menu: SitemapResource["menus"][number],
  messages: Readonly<Record<string, string>>,
): string {
  const byMenuId = messages[`nexmux.menu.${menu.id}.title`];
  if (byMenuId) return byMenuId;
  if (menu.pageId && messages[`nexmux.page.${menu.pageId}.title`]) {
    return messages[`nexmux.page.${menu.pageId}.title`];
  }
  return menu.title;
}

function localizeMenus(
  menus: SitemapResource["menus"],
  messages: Readonly<Record<string, string>>,
): SitemapResource["menus"] {
  return menus.map((menu) => ({
    ...menu,
    title: resolveMenuTitle(menu, messages),
    ...(menu.type === "group" ? { children: localizeMenus(menu.children ?? [], messages) } : {}),
  }));
}

function enrichMenusWithPaths(
  menus: SitemapResource["menus"],
  pages: SitemapFile["pages"],
): SitemapResource["menus"] {
  const pathByPageId = new Map(pages.map((page) => [page.id, page.path]));
  const walk = (items: SitemapResource["menus"]): SitemapResource["menus"] =>
    items.map((item) => {
      const next = { ...item };
      if (item.pageId && !item.path) {
        const path = pathByPageId.get(item.pageId);
        if (path) next.path = path;
      }
      if (item.type === "group" && item.children) next.children = walk(item.children);
      return next;
    });
  return walk(menus);
}

export function buildSitemap(
  user: DemoUser | null,
  i18n: RequestI18n,
  navigationMenus: SitemapResource["menus"],
): SitemapResource {
  const { site } = state();
  const pages: SitemapResource["pages"] = site.pages.map((page) => ({
    id: page.id,
    path: page.path,
    title: i18n.messages[`nexmux.page.${page.id}.title`] ?? page.title,
    version: page.version,
  }));
  const layouts = localizeValue(site.layouts, i18n.messages) as SitemapFile["layouts"];
  const visiblePageIds = new Set<string>();
  const collect = (items: SitemapResource["menus"]) => {
    for (const item of items) {
      if (item.pageId) visiblePageIds.add(item.pageId);
      if (item.children) collect(item.children);
    }
  };
  collect(navigationMenus);
  const routeEntries: SitemapResource["menus"] = site.pages
    .filter((page) => !visiblePageIds.has(page.id))
    .map((page) => ({
      id: page.id,
      type: "page" as const,
      pageId: page.id,
      title: i18n.messages[`nexmux.page.${page.id}.title`] ?? page.title,
      layout: page.layout ?? (page.id === "login" ? "login" : "admin"),
      hidden: true,
      ...(page.public ? { public: true } : {}),
      ...(page.activeMenu || ACTIVE_MENU[page.id]
        ? { activeMenu: page.activeMenu ?? ACTIVE_MENU[page.id] }
        : {}),
    }));
  return {
    ...site,
    layouts,
    updatedAt: new Date().toISOString(),
    ...(user
      ? { user: { ...user, name: user.name?.trim() || user.username } as unknown as Record<string, unknown>, permissions: user.permissions }
      : {}),
    pages,
    menus: localizeMenus(enrichMenusWithPaths([...navigationMenus, ...routeEntries], site.pages), i18n.messages),
  };
}

export function getLazyPage(
  pageId: string,
  user: DemoUser | null,
  i18n: RequestI18n,
): { version: number; pageDsl: PageDsl } | null {
  const { site, pages } = state();
  const entry = site.pages.find((page) => page.id === pageId);
  const page = pages.get(pageId);
  if (!entry?.lazy || !page) return null;
  if (!entry.public && !user) return null;
  return {
    version: entry.version,
    pageDsl: localizeValue(page.pageDsl, i18n.messages) as PageDsl,
  };
}

export type { PageEntry };

export function getLoginPath(): string {
  return state().site.auth.loginPath ?? "/login";
}

export function findPageEntryById(pageId: string): PageEntry | undefined {
  return state().site.pages.find((page) => page.id === pageId);
}

export function findPageDslById(pageId: string): PageDsl | undefined {
  return state().pages.get(pageId)?.pageDsl;
}

export function findPageEntryByPath(pathname: string): PageEntry | null {
  const { site } = state();
  const path = normalizeRoutePath(pathname);
  const matched = site.pages
    .map((page, index) => ({ page, index }))
    .filter(({ page }) => matchRoutePath(page.path, path))
    .sort((a, b) => staticSegmentCount(b.page.path) - staticSegmentCount(a.page.path) || a.index - b.index)[0];
  return matched?.page ?? null;
}

function splitPath(value: string): string[] {
  return normalizeRoutePath(value).split("/").filter(Boolean).map((segment) => {
    try {
      return decodeURIComponent(segment);
    } catch {
      return segment;
    }
  });
}

function matchRoutePath(pattern: string, pathname: string): boolean {
  const patternSegments = splitPath(pattern);
  const pathSegments = splitPath(pathname);
  return patternSegments.length === pathSegments.length
    && patternSegments.every((segment, index) => segment.startsWith(":") || segment === pathSegments[index]);
}

function staticSegmentCount(pattern: string): number {
  return splitPath(pattern).filter((segment) => !segment.startsWith(":")).length;
}

function normalizeRoutePath(pathname: string): string {
  const path = pathname.split("?")[0] ?? pathname;
  return path === "/" ? "/" : path.replace(/\/+$/, "");
}
