import path from "node:path";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";

import { withShell } from "@pactor-app/shell/next";

const nextConfig: NextConfig = withShell({
  transpilePackages: ["nexmux-ui-app", "nexmux-ui-general-app"],
  turbopack: {
    root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../.."),
  },
  /** 根路径与旧工作台入口统一进入 nexmux 仪表盘（页面 path `/dashboard` + basePath `/nexmux`）。 */
  async redirects() {
    return [
      { source: "/", destination: "/nexmux/dashboard", permanent: false },
      { source: "/nexmux", destination: "/nexmux/dashboard", permanent: false },
      { source: "/workspace", destination: "/nexmux/dashboard", permanent: false },
      {
        source: "/general-app/workspace",
        destination: "/nexmux/dashboard",
        permanent: false,
      },
    ];
  },
});

export default nextConfig;
