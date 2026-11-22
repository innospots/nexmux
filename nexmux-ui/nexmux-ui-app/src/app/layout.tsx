import type { Metadata } from "next";
import type { ReactNode } from "react";
import { PactorApp } from "@/frontend/App";
// 顺序约定：globals（本项目 tailwind 工具类 + 外壳规则）→
// ui 预建样式 → login.css。主题与皮肤由 Shell 提供，本模块不再注入皮肤。
import "./globals.css";
import "@pactor-app/ui/styles.css";
import "./login.css";

export const metadata: Metadata = {
  title: "Nexmux Model Console",
  description: "Nexmux Model Console 管理台",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html
      lang="zh-CN"
      suppressHydrationWarning
      className="h-full antialiased"
    >
      <body className="h-full flex flex-col">
        {/*
         * PactorApp 挂在 Next layout 而非 page：App Router 导航时 page 会
         * 重挂载、layout 常驻——sitemap 只拉取一次，切换导航只刷新内容区。
         * children（page）恒为 null，路由由 PactorApp 内部按 sitemap 解析。
         */}
        <PactorApp />
        {children}
      </body>
    </html>
  );
}
