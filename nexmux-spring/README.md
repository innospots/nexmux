# nexmux-spring

Nexmux 运营平台可运行组装：Spring Boot 宿主 + `nexmux-kernel` 业务与控制台插件。默认 HTTP 端口 **8082**（见 `application.yaml`）。

## 服务启动

### 前置

- **JDK 25+**（发行包脚本 `bin/startup.sh` 依赖 `JAVA_HOME`）
- 先在本机安装 Nexus 平台依赖（见仓库根 `AGENTS.md`）：

```bash
cd ../innospots-nexus && mvn clean install -DskipTests
cd ../nexmux && mvn validate clean compile
```

### 本地开发（推荐）

本工程**不使用** `mvn spring-boot:run`。在 IDE 中直接运行主类 **`com.innospots.nexmux.spring.NexmuxPlatformServer`** 的 `main` 方法（与发行包 `bin/setenv.sh` 中 `SERVER_MAIN_CLASS` 一致）。

| 项 | 建议 |
|----|------|
| 运行模块 | `nexmux-spring`（classpath 含 `nexmux-kernel` 与 Nexus 平台依赖） |
| 工作目录 | **`nexmux-spring` 模块根**（H2 文件库相对路径 `./out/db/`，见 `application-local.yaml`） |
| Profile | 不设 VM 选项时，`application.yaml` 的 `spring.profiles.default=local` 生效；也可显式 `-Dspring.profiles.active=local` |

先执行 `mvn validate clean compile`（或 `package`）确保依赖与资源已就绪，再在 IDE 中 Run/Debug `NexmuxPlatformServer`。

健康检查：

```bash
curl -s http://localhost:8082/actuator/health
```

### 发行包

```bash
cd .. && mvn package -pl nexmux-spring -am -DskipTests
```

产物：`nexmux-spring/dist/nexmux-spring.tar.gz`。解压后目录结构：

```text
nexmux-spring/
  bin/      startup.sh | shutdown.sh | restart.sh | setenv.sh
  config/   自 src/main/resources 拷贝（含 nexus/、schema/、application*.yaml）
  lib/      运行时依赖 + 本模块 jar
  logs/     运行日志（nexmux.log）
```

启动（**请传入 profile**，与 `application-local.yaml` / `application-prod.yaml` 对应）：

```bash
export JAVA_HOME=/path/to/jdk-25
bin/startup.sh local    # H2 文件库
# bin/startup.sh prod   # MySQL，需 DB_* 等环境变量，见 application-prod.yaml
```

停止 / 重启：`bin/shutdown.sh [profile]`、`bin/restart.sh [profile]`。

运行时可通过 **`config/`** 覆盖 classpath 配置（`startup.sh` 已设置 `spring.config.additional-location=optional:file:${CONFIG_DIR}/`），例如 `config/nexus/console-web.yaml`。

主类：`com.innospots.nexmux.spring.NexmuxPlatformServer`。

---

## 打开 UI

### 1. API 文档（Scalar / OpenAPI）

服务启动后浏览器访问：

| 说明 | URL |
|------|-----|
| Scalar 文档页 | http://localhost:8082/openapi/ui |
| 规范目录 | http://localhost:8082/openapi/specs |
| 单份规范 | http://localhost:8082/openapi/specs/{specId} |

开关与标题见 `src/main/resources/nexus/console-openapi.yaml`（`scalar.enabled`、`scalar.path`）。

### 2. 动态页面前端（nexmux-ui）

控制台 UI 在独立工程 **`nexmux-ui`**（Next.js，开发端口 **9011**）。接入本后端时需让浏览器 **CORS 直连 API**（`console-web.yaml` 中本地示例已开启 CORS）。

1. 启动本服务（上节，端口 8082）。
2. 启动前端：

```bash
cd ../nexmux-ui
pnpm install
pnpm dev   # http://localhost:9011
```

3. 修改 `nexmux-ui/src/pactor/config.ts`（或构建时设环境变量）：

```ts
export const sitemapSources: string[] = ["/api/public/sitemap/nexmux"];
export const apiBaseUrl = "http://localhost:8082";
```

等价环境变量：`NEXT_PUBLIC_API_BASE_URL=http://localhost:8082`。

4. 若使用懒加载页面，为 `SitemapApp` 配置与后端一致的页面端点（框架默认 `/api/pages/{id}`，Nexmux 后端为 **`/api/public/pages/{pageKey}`**，见下节）。

Sitemap 与页面 YAML 源码在 **`nexmux-kernel/src/main/resources/ui-pages/nexmux/`**；前端行为说明见 `nexmux-ui/README.md`。

---

## 动态页面 HTTP 接口

公共前缀：`/api/public`（Nexus `ConsoleConstant.PUBLIC_API_PREFIX`）。

### Sitemap

加载 Nexmux 产品 sitemap（`ui-pages/nexmux/sitemap.yaml`）：

```http
GET /api/public/sitemap/nexmux
Accept: application/json
```

响应为统一包裹 `R<SitemapResource>`（`code` / `data` / `message`）。`data` 含 `layouts`、`pages`、`menus`、`auth` 等，供 `@pactor-app/app` 的 `SitemapApp` 驱动路由与导航。

示例：

```bash
curl -s http://localhost:8082/api/public/sitemap/nexmux
```

### 页面 PageDsl（懒加载 / 查看 DSL 结构）

按 classpath 加载并渲染 PageDsl（可带 query 参与过滤器链）：

```http
GET /api/public/pages/{pageKey}
Accept: application/json
```

路径参数 **`pageKey`** 为 Nexus 编码：**`{domainKey}-{moduleKey}-{page.id}`**（仅前两段用 `-` 分隔，`page.id` 本身可含 `-`）。

| 字段 | Nexmux 固定/示例 |
|------|------------------|
| `domainKey` | `nexmux` |
| `moduleKey` | `provider` / `model` / `route` / `client` / `ops` / `debug` 等 |
| `page.id` | 与 yaml 中 `page.id`、sitemap 中 `pages[].id` 一致，如 `nexmux-provider-list` |

示例（Provider 列表页）：

```bash
curl -s "http://localhost:8082/api/public/pages/nexmux-provider-nexmux-provider-list"
```

成功时 `data` 为完整 **PageDsl** JSON（`dsl`、`page`、`meta`、`state`、`dataSources`、`body` 等），可用于对照页面结构或与 UI 懒加载对齐。

OpenAPI 中可搜索 `PageDsl` / `uiSitemapRenderNexmux` 相关 operation。

---

## 页面定义怎么用

三层约定（细节见 `docs/design/nexmux-yaml-configuration-design.md`、`docs/design/nexmux-ui-spec-pages-design.md`）：

### 1. Sitemap（入口）

- 文件：`nexmux-kernel/src/main/resources/ui-pages/nexmux/sitemap.yaml`
- 声明站点 `id`、布局 DSL、`pages[]`（`id` / `path` / `title` / `lazy` / `public` 等）、`menus` 树、`auth.loginPath`
- **`lazy: true`**：不在 sitemap 响应内联 `pageDsl`，由客户端调用 **`GET /api/public/pages/{pageKey}`** 按需加载

### 2. PageDsl（单页契约）

- 路径：**`ui-pages/nexmux/{moduleKey}/{pageKey}.yaml`**
- **`page.id`**、文件名（无后缀）与控制台 Entry 的 **`pageKey`** 必须一致
- **`meta.routePath`** 与 SPA 路由一致：`/page/nexmux/{moduleKey}/{pageKey}`（见 Entry 插件）
- **`meta.apiBase`** 应与 `nexus/nexmux.yaml` 中 **`nexmux.console.api-base-path`**（默认 `/api/nexmux`）一致
- `dataSources` 中相对 URL 基于 `meta.apiBase` 解析到 Nexmux 管理 API

### 3. 控制台目录（Console Entry）

- Java：`NexmuxConsoleEntryPlugin` 注册模块、菜单与 `pageKey`；与 catalog 同步
- 页面级 API 鉴权（开启安全时）在匹配路径上要求请求头 **`X-Nexus-Page-Key: {pageKey}`**（见下节）

新增页面时：先 Entry + catalog 约定 → 添加 PageDsl yaml → 在 `sitemap.yaml` 增加 `pages` / 菜单项 → 跑 kernel 侧契约测试（如 `NexmuxPageDslClasspathTest`）。

---

## 鉴权开关

鉴权分两层，**不要混在同一文件里改错项**：

| 层级 | 配置文件 | 键 | 作用 |
|------|----------|-----|------|
| 登录 / 令牌 | `nexus/console-auth.yaml` | `nexus.console.auth.*` | AES 密钥、access/refresh TTL、验证码、RSA 私钥等 |
| Jersey 请求安全 | `nexus/console-web.yaml` | `nexus.console.web.security.enabled` | **总开关**：Bearer 鉴权 + 控制台页面 permission 过滤器 |
| 本地调试身份 | 同上 | `nexus.console.web.security.dev-session.*` | 仅当 **`security.enabled=false`** 时注入固定 `SessionContext`（**禁止生产开启**） |
| 页面级授权 | 同上 | `security.console-path-patterns`、`page-key-header` | 命中路径时在已登录前提下校验 **`X-Nexus-Page-Key`** |
| 匿名白名单 | 同上 | `security.permit-all-patterns` | **`security.enabled=true`** 时跳过 Bearer 的路径（如 `/openapi/**`、登录、health） |

**仓库默认示例**（便于本地联调 UI）：`console-web.yaml` 中

- `nexus.console.web.security.enabled: false`
- `dev-session.enabled: true`

生产环境应 **`security.enabled: true`**、**`dev-session.enabled: false`**，并更换 `console-auth.yaml` 中的 `token-secret` 与 RSA 密钥；CORS 若带 Cookie，需关闭 `allowed-origins: "*"` 并配置具体源。

`/api/public/**` 是否需登录取决于 Nexus 过滤器与 `permit-all-patterns` 的装配；联调时若 401，可临时关闭 `security.enabled` 或把公共 API 加入 permit 列表（生产按安全要求收紧）。

---

## `src/main/resources/nexus` 配置说明

由根 `application.yaml` 的 **`spring.config.import`** 引入（均为 `optional:classpath:nexus/...`）。发行包中同名文件位于 **`config/nexus/`**，可直接编辑覆盖。

| 文件 | 前缀 / 绑定 | 说明 |
|------|-------------|------|
| **`nexmux.yaml`** | `nexmux.*` | 产品业务：Console API 前缀 `nexmux.console.api-base-path`（默认 `/api/nexmux`）、AppKey 宽限期、调试响应体上限、Runtime 轮询间隔等。**不含** JDBC、插件列表、Filter 开关 |
| **`console-auth.yaml`** | `nexus.console.auth.*` | 登录与令牌策略（见上表） |
| **`console-credential.yaml`** | `nexus.console.credential.totp.*` | TOTP 主密钥等凭据能力 |
| **`console-web.yaml`** | `nexus.console.web.*` | Web 横切：总开关 `enabled`、安全/CORS/dev-session（见上节） |
| **`console-openapi.yaml`** | `scalar.*` | Scalar 文档页与 OpenAPI HTTP 出口 |
| **`console-plugins.yaml`** | `nexus.plugin.*`、`nexus.plugins.*` | 插件自动安装、`required` 列表（须含 **`com.innospots.nexmux.console`**） |
| **`schema/`** | — | 产品 H2 增量脚本（如 `nexmux-h2.sql`），由 `application-local.yaml` 的 `spring.sql.init` 与根 `schema/` 脚本一起初始化库 |

产品键规范与禁止项：仓库 **`docs/design/nexmux-yaml-configuration-design.md`**。

本地覆盖示例（不修改 jar 内文件）：

```text
./config/nexus/console-web.yaml
./config/nexus/nexmux.yaml
```

（配合发行包 `config/` 或 IDE 运行时 `spring.config.additional-location`。）

---

## 相关文档

- 仓库构建与模块边界：根目录 `AGENTS.md`
- YAML 总规范：`docs/design/nexmux-yaml-configuration-design.md`
- UI 接入 sitemap / 懒加载：`nexmux-ui/README.md`
