# Contributing to Nexmux

感谢贡献代码、文档、测试与问题反馈。请同时阅读 [README.md](./README.md)、[AGENTS.md](./AGENTS.md) 和 [工程结构指南](./docs/nexmux-engineering.md)。

## 开发环境

- JDK 25+
- Maven 3.9+
- Node.js 22+ 与 pnpm
- 可选：Playwright、真实或本地 OpenAI 上游

平台仓库与产品仓库放在同一父目录：

```text
workspace/
├── innospots-nexus/
└── nexmux/
```

安装依赖并验证：

```bash
cd ../innospots-nexus && mvn clean install -DskipTests
cd ../nexmux && mvn validate clean test
cd nexmux-ui && pnpm install --frozen-lockfile && pnpm build
```

## 分支与提交

- 功能/修复从 `develop` 创建短生命周期分支，保持提交可审查、可回滚。
- 使用 Conventional Commits：`feat(runtime): ...`、`fix(model): ...`、`docs(nexmux): ...`、`test(runtime): ...`。
- 不提交无关格式化、生成物、临时数据库、日志、密钥或个人信息。
- 不把大型功能与文档治理、机械迁移混成一个无法审查的提交；Runtime 实现与文档可分别提交。

## 工程约束

1. 仅使用 `nexmux-bom`、`nexmux-kernel`、`nexmux-spring`、`nexmux-ui`，不新增 Maven 模块。
2. 不复制 Nexus 源码，不内联 JAR 版本。
3. `core` 业务服务保持纯 Java，Spring 装配集中在 `nexmux-spring`。
4. 端点使用 Jakarta REST，不使用 Spring MVC、Spring Data 或 Spring Security 注解。
5. 遵守产品边界：不做同步引擎、数据血缘或客户账单；金额仅为内部分析口径。
6. 不返回伪成功。未实现能力应明确失败，不能用空实现或成功占位掩盖。
7. Console 配置变更须同时考虑持久化、回显、Runtime 生效、失败路径和清理。

## 测试要求

- Bug 修复先写复现测试，确认修复前失败，再修改实现。
- 新增公共行为至少覆盖正常路径与主要拒绝路径。
- 每批 Java 改动后执行 `mvn clean compile`；交付前必须通过 `mvn test`。
- 不通过删除断言、注释测试、放宽错误码或降低 JDK/enforcer 来“修复”失败。
- Console 配置项尽量增加 Console → Runtime 端测，使用独立解包目录和临时数据库。
- 真实上游测试默认使用本地桩；真实供应商调用需控制费用、凭证和测试数据清理。

常用命令：

```bash
mvn -o clean compile
mvn -o test
mvn -o validate
mvn -o package -pl nexmux-spring -am -DskipTests

cd nexmux-ui
pnpm lint
pnpm build
```

## 文档要求

- 新能力同时更新 README、接入/部署/工程文档和测试范围。
- 配置键说明默认值、读取方、生效时间、失败行为和生产风险。
- 不把“页面存在”写成“功能已实现”，不把“单测通过”写成“端测通过”。
- 历史设计与旧报告放入 `docs/archive/`，不与当前事实源并列。
- 协议、路径、状态码、配置键和示例必须可复制、可验证。

## Pull Request 检查单

- [ ] 需求与模块边界清楚，未引入不必要的 abstraction/wrapper。
- [ ] 复现测试曾失败，修复后全部通过。
- [ ] `mvn validate test` 通过，前端构建通过（涉及 UI 时）。
- [ ] `git diff --check` 通过，无生成物、临时文件或敏感信息。
- [ ] 公共 API、数据库、配置键、错误码与兼容说明已更新。
- [ ] 相关端测已执行，BLOCKED 项说明原因，未隐藏失败。
- [ ] 文档、变更记录和迁移说明已更新。

## 安全与审查

- 不在 PR、日志或评论中贴真实密钥、Token、业务 Prompt 或数据库文件。
- 安全漏洞走 [SECURITY.md](./SECURITY.md) 的私密渠道，不提交公开漏洞复现。
- 代码审查重点关注：权限、凭证、异常语义、金额、事务、并发、兼容性和测试真实性。
