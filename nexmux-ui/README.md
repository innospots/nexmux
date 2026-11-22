# nexmux-ui

Nexmux 管理台前端。工作区包含业务应用、通用管理模块与 Shell；模块均为本仓库自有源码，Pactor 仅作为 npm 依赖。

| 目录 | 作用 |
| --- | --- |
| `nexmux-ui-app` | 模型平台业务。可独立运行，也可打成 Pactor UI 模块 |
| `nexmux-ui-shell` | Web Shell。装配 `nexmux-ui-app` 与 `nexmux-ui-general-app` |
| `nexmux-ui-general-app` | 通用管理：系统设置、字典、角色、用户、审计日志。源码源自 Pactor `general-app` starter，已拷贝为本仓库自有模块（不再 link Pactor） |

```bash
pnpm install
pnpm dev            # 独立应用 http://localhost:9011
pnpm build:module   # 产出 nexmux-ui-app / nexmux-ui-general-app 的 dist-module
pnpm assemble       # 装配进 shell
pnpm dev:shell      # Shell http://localhost:9020
```

Pactor 依赖为 npm `1.72.0`（`pactor-app`、`@pactor-app/core`、`runtime`、`ui`、`shell`、`cli`）。
