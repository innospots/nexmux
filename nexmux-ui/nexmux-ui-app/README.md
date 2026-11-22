# nexmux-ui-app

Nexmux 模型控制台。同一套页面、组件和业务 API 支持两种运行方式：

- **独立应用**：`pnpm dev`（http://localhost:9011）。`src/app` 挂载 `SitemapApp`，`/api/sitemap` 与 `/api/public/pages` 提供页面，`/api/d` 转发到后端。
- **UI 模块**：`pnpm build:module` 产出 `dist-module/`。Shell 按 `basePath` `/nexmux` 装配页面，业务 API 挂到 `/api/modules/nexmux/*`。

## 结构

| 路径 | 说明 |
| --- | --- |
| `src/app/` | 独立运行入口（layout、sitemap、懒加载页面、业务 API 薄路由） |
| `src/frontend/` | `SitemapApp` 接线、自定义组件、动作、页面样式 |
| `src/backend/` | 会话、页面 DSL、以及独立运行与模块共用的 API 实现 |
| `module/client.ts` | 模块 client：组件 / 动作注册，并改写浏览器里的逻辑 API 路径 |
| `module/server.ts` | 模块 server：`/api/d`、`/api/auth`、`/api/i18n`、`/api/users` |
| `pactor.module.json` | 模块 id `nexmux`、`basePath`、API 前缀 |
| `public/` | 静态资源，随模块分发 |

## 命令

```bash
pnpm dev
pnpm build
pnpm build:module
pnpm pack:module
```
