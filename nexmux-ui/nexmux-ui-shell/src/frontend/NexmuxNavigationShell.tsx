"use client";

import {
  Activity,
  Blocks,
  ChartNoAxesCombined,
  FileText,
  KeyRound,
  LayoutDashboard,
  MessageSquare,
  Palette,
  ScrollText,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Terminal,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { isValidElement, useEffect, useMemo, useRef, type ReactNode } from "react";

import { useRenderer } from "@pactor-app/runtime";
import {
  deriveNavigationBreadcrumb,
  findNavigationMenu,
  createNavigationRegistry,
  type NavigationMenuItem,
} from "@pactor-app/shell/client";

/** 判断 prop 值是否为 DSL 节点（{ type: string } 裸对象），否则按 ReactNode 处理 */
function isDslNode(value: unknown): value is { type: string } {
  return (
    typeof value === "object" &&
    value !== null &&
    !isValidElement(value) &&
    typeof (value as { type?: unknown }).type === "string"
  );
}

function RailSlot({ content }: { content: ReactNode }) {
  if (isDslNode(content)) {
    return <RailSlotInner node={content} />;
  }
  return <>{content}</>;
}

function RailSlotInner({ node }: { node: { type: string } }) {
  const { renderChildren } = useRenderer();
  return <>{renderChildren([node as never])}</>;
}

function navId(item: NavigationMenuItem): string | undefined {
  return item.id ?? item.key ?? item.path;
}

function navPath(item: NavigationMenuItem): string | undefined {
  return item.path ?? (item.key?.startsWith("/") ? item.key : undefined);
}

function menuTitle(item: NavigationMenuItem): string {
  return item.title ?? item.label ?? "";
}

function isGroup(item: NavigationMenuItem): boolean {
  return item.type === "group" || (item.children !== undefined && item.children.length > 0);
}

function visibleChildren(group: NavigationMenuItem): NavigationMenuItem[] {
  return (group.children ?? []).filter((item) => !item.hidden && navPath(item));
}

function formatBadge(badge: string | number | undefined): string | null {
  if (badge === undefined || badge === null) {
    return null;
  }
  const value = String(badge).trim();
  if (value === "" || value === "0") {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 99 ? "99+" : value;
}

/** 一级导航图标：按菜单语义选择 lucide 图标，未知名称回退 message-square */
const ICONS: Record<string, LucideIcon> = {
  dashboard: LayoutDashboard,
  "layout-dashboard": LayoutDashboard,
  home: LayoutDashboard,
  workspace: LayoutDashboard,
  assets: Blocks,
  boxes: Blocks,
  layers: Blocks,
  models: Blocks,
  "file-text": FileText,
  file: FileText,
  access: Users,
  users: Users,
  clients: UserCog,
  shield: ShieldCheck,
  "shield-check": ShieldCheck,
  operations: Activity,
  activity: Activity,
  chart: ChartNoAxesCombined,
  "chart-no-axes-combined": ChartNoAxesCombined,
  analytics: ChartNoAxesCombined,
  debug: Terminal,
  terminal: Terminal,
  console: Terminal,
  "message-square": MessageSquare,
  settings: Settings,
  system: Settings,
  "sliders-horizontal": SlidersHorizontal,
  audit: ScrollText,
  "scroll-text": ScrollText,
  key: KeyRound,
  "key-round": KeyRound,
  palette: Palette,
  themes: Palette,
  user: UserCog,
};

function iconFor(name: string | undefined): LucideIcon {
  return ICONS[(name ?? "").toLowerCase()] ?? MessageSquare;
}

function groupContains(group: NavigationMenuItem, currentId: string | undefined): boolean {
  if (!currentId) {
    return false;
  }
  return visibleChildren(group).some(
    (item) =>
      navId(item) === currentId || item.pageId === currentId || item.activeMenu === currentId,
  );
}

export interface NexmuxNavigationShellProps {
  menus?: NavigationMenuItem[];
  brandMark?: string;
  brandHref?: string;
  /** 主内容区顶部面包屑（按菜单树推导），缺省 true */
  breadcrumb?: boolean;
  /** 图标轨悬停展开（显示文案），缺省 true */
  expandRailOnHover?: boolean;
  railFooter?: ReactNode;
  children?: ReactNode;
}

/**
 * Nexmux 一级导航壳：语义化 lucide 图标 + 悬停展开文案。
 * props 与 @pactor-app/shell 的 NavigationShell 对齐，作为 DSL 组件同名覆盖注册。
 */
export function NexmuxNavigationShell({
  menus = [],
  brandMark = "N",
  brandHref = "/",
  breadcrumb: breadcrumbProp = true,
  expandRailOnHover = true,
  railFooter,
  children,
}: NexmuxNavigationShellProps) {
  const pathname = usePathname() ?? "/";
  const router = useRouter();
  const mainRef = useRef<HTMLDivElement>(null);

  const topLevelMenus = useMemo(
    () =>
      menus.filter(
        (item) =>
          !item.hidden &&
          (isGroup(item) ? visibleChildren(item).length > 0 : Boolean(navPath(item))),
      ),
    [menus],
  );

  const registry = useMemo(() => createNavigationRegistry(menus), [menus]);
  const matchedMenu = findNavigationMenu(registry, pathname);
  const currentMenuId = matchedMenu?.activeMenu ?? matchedMenu?.id;

  const selectedTopLevel = useMemo(
    () =>
      topLevelMenus.find(
        (item) => navId(item) === currentMenuId || groupContains(item, currentMenuId),
      ),
    [currentMenuId, topLevelMenus],
  );

  const activeGroup = selectedTopLevel && isGroup(selectedTopLevel) ? selectedTopLevel : undefined;

  const breadcrumbEntries = useMemo(
    () => (breadcrumbProp ? deriveNavigationBreadcrumb(menus, pathname) : []),
    [breadcrumbProp, menus, pathname],
  );

  useEffect(() => {
    const content = mainRef.current?.querySelector<HTMLElement>(".shell-content");
    content?.scrollTo({ top: 0, left: 0, behavior: "instant" });
  }, [pathname]);

  function selectGroup(group: NavigationMenuItem) {
    if (groupContains(group, currentMenuId)) {
      return;
    }
    const first = visibleChildren(group)[0];
    const firstPath = first ? navPath(first) : undefined;
    if (firstPath) {
      router.push(firstPath);
    }
  }

  return (
    <div className="kn-shell">
      <nav
        className={expandRailOnHover ? "kn-rail kn-rail-expandable" : "kn-rail"}
        aria-label="主导航"
      >
        {brandHref === "" ? (
          <div className="kn-mark" aria-hidden="true">
            {brandMark}
          </div>
        ) : (
          <Link href={brandHref} className="kn-mark" aria-label="返回首页">
            {brandMark}
          </Link>
        )}
        {topLevelMenus.map((item) => {
          const Icon = iconFor(item.icon);
          const itemId = navId(item);
          const title = menuTitle(item);
          const badge = formatBadge(item.badge);
          const active = selectedTopLevel !== undefined && navId(selectedTopLevel) === itemId;
          const content = (
            <>
              <Icon size={18} strokeWidth={1.8} />
              <span className="kn-rail-label">{title}</span>
              {badge !== null && <span className="kn-rail-badge">{badge}</span>}
            </>
          );

          return isGroup(item) ? (
            <button
              key={itemId}
              type="button"
              className={active ? "kn-rail-button is-active" : "kn-rail-button"}
              aria-label={title}
              aria-pressed={active}
              onClick={() => selectGroup(item)}
            >
              {content}
            </button>
          ) : (
            <Link
              key={itemId}
              href={navPath(item) ?? "/"}
              className={active ? "kn-rail-button is-active" : "kn-rail-button"}
              aria-label={title}
              aria-current={active ? "page" : undefined}
            >
              {content}
            </Link>
          );
        })}
        {railFooter !== undefined && railFooter !== null && (
          <div className="kn-rail-footer">
            <RailSlot content={railFooter} />
          </div>
        )}
      </nav>

      {activeGroup ? (
        <aside className="kn-sidebar" aria-label={menuTitle(activeGroup)}>
          <div className="kn-sidebar-title">{menuTitle(activeGroup)}</div>
          <nav className="kn-menu">
            {visibleChildren(activeGroup).map((item) => {
              const path = navPath(item)!;
              const badge = formatBadge(item.badge);
              const active = navId(item) === currentMenuId || item.pageId === currentMenuId;
              return (
                <Link
                  key={navId(item)}
                  href={path}
                  aria-current={active ? "page" : undefined}
                  className={active ? "kn-menu-item is-active" : "kn-menu-item"}
                >
                  <span className="kn-menu-label">{menuTitle(item)}</span>
                  {badge !== null && <span className="kn-menu-badge">{badge}</span>}
                </Link>
              );
            })}
          </nav>
        </aside>
      ) : null}

      <div className="kn-main" ref={mainRef}>
        {breadcrumbEntries.length > 0 && (
          <nav className="kn-breadcrumb" aria-label="面包屑">
            {breadcrumbEntries.map((entry, index) => {
              const isLast = index === breadcrumbEntries.length - 1;
              return (
                <span key={entry.id} className="kn-breadcrumb-item">
                  {index > 0 && (
                    <span className="kn-breadcrumb-sep" aria-hidden="true">
                      /
                    </span>
                  )}
                  {isLast || !entry.path ? (
                    <span className="kn-breadcrumb-text" aria-current={isLast ? "page" : undefined}>
                      {entry.title}
                    </span>
                  ) : (
                    <Link href={entry.path} className="kn-breadcrumb-link">
                      {entry.title}
                    </Link>
                  )}
                </span>
              );
            })}
          </nav>
        )}
        {children}
      </div>
    </div>
  );
}
