# nexmux-ui-shell

Nexmux 的 Web Shell，结构对齐 Pactor `web-shell`：构建期装配 UI 模块，运行期由 `PactorShellHost` 渲染页面。

当前只启用 `nexmux-ui-app`（模块 id `nexmux`）。导航在 `src/shell/sitemap.yaml`。业务数据仍走模块 API `/api/modules/nexmux/*`，平台登录在 `/nexmux/login`。Shell 自带的演示会话只服务外壳登录页，不替代平台 cookie。

```bash
# 在 nexmux-ui 工作区根目录
pnpm build:module
pnpm assemble
pnpm --filter nexmux-ui-shell dev    # http://localhost:9020
```

`pnpm build` 的顺序是装配、校验、同步皮肤，然后 `next build`。
