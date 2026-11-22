"use client";

import { AppShell, PactorShellHost } from "@pactor-app/shell/client";

import { moduleExtensions } from "@generated/pactor/client-registry";

import { NexmuxNavigationShell } from "./NexmuxNavigationShell";

/**
 * 工作区外壳：sitemap 模式（菜单/布局由 Pactor DSL 布局链承担）。
 * 业务组件由各模块的 client entry 注册（extensions.components），
 * Shell 不再内建业务组件；这里只负责把装配产物交给 PactorShellHost。
 * NavigationShell 用 Nexmux 版本覆盖：语义化一级导航图标 + 悬停展开文案。
 */
export function WorkspaceShell() {
  return (
    <AppShell
      menu={{ mode: "sitemap" }}
      layout={{ variant: "side-fixed" }}
      renderSubApp={() => (
        <PactorShellHost
          extensions={moduleExtensions}
          components={{ NavigationShell: NexmuxNavigationShell }}
        />
      )}
    />
  );
}
