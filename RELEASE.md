# Nexmux 发布流程

本文定义版本、构建、验证、发行包、推送与回滚流程。普通功能提交不自动创建 Release。

## 版本规则

- 采用 `MAJOR.MINOR.PATCH` 与 `-SNAPSHOT` 开发标识。
- `MAJOR`：破坏性 API、数据库或配置不兼容。
- `MINOR`：向后兼容的新能力。
- `PATCH`：向后兼容的缺陷修复与文档修订。
- 发布前在 `CHANGELOG.md` 写明变更、兼容性和迁移要求；不要虚构版本日期或发布状态。

## 发布前检查

```bash
cd ../innospots-nexus && mvn clean install -DskipTests
cd ../nexmux
mvn validate clean test
mvn package -pl nexmux-spring -am -DskipTests
cd nexmux-ui && pnpm install --frozen-lockfile && pnpm lint && pnpm build
```

同时确认：

- Console → Runtime 端测已执行并记录结果；FAIL 均有结论或修复，BLOCKED 有环境说明。
- 新 tar 的独立解包目录已验证，避免“构建成功但运行旧包”。
- `git diff --check` 通过，无临时数据库、日志、密钥或生成物。
- `standard_usd`、`config_revision` 等 schema 变更可幂等迁移，旧库有备份。
- 文档、API、状态码、配置键、兼容性和迁移说明已同步。
- CI 通过，工作区只包含目标发布提交。

## 发行物

- `dist/nexmux-spring.tar.gz`：Console 与 Runtime 共用发行包。
- `nexmux-kernel` 构建产物中的 `META-INF/nexus-openapi/nexmux-kernel.yaml`：机器可读 API 契约。
- `CHANGELOG.md` 对应版本段：作为标签与 GitHub Release notes 来源。

## 提交与推送

建议 Runtime 实现与文档分开提交：

```bash
git add nexmux-kernel nexmux-spring
git commit -m "feat(runtime): <change>"

git add README.md docs nexmux-ui
git commit -m "docs(nexmux): <documentation>"
```

推送到目标分支后，确认本地 `HEAD` 与远程一致，并等待 CI 成功。不要为推送而修改提交作者或重写公开历史。

## 创建版本标签（需要正式发布时）

1. 确定版本号和 `CHANGELOG.md` 版本段，完成上述验证。
2. 将版本段写入独立 release notes 文件，不包含密钥或临时绝对路径。
3. 创建 annotated tag，例如 `v0.2.0`，并先推送提交和标签。
4. 若使用 GitHub Release，确认标签已存在远程，再创建/编辑 release 并附发行包与说明。

除非明确要求正式发布，不自动升级版本、创建标签或 GitHub Release。

## 部署与回滚

- fresh 库先启动 Console 执行 DDL/种子，再启动 Runtime。
- 升级前停止 Console 与 Runtime，备份数据库、配置和当前发行包。
- 保留原数据库目录，使用新 tar 的解包目录启动并执行只读健康检查。
- 验证 AppKey、已发布 Alias、调用、日志、定价与配置版本后逐步开放流量。
- 若回滚应用版本，先停止新进程，再启动保留的旧发行包；不要直接覆盖唯一数据库副本。
- 数据库结构回滚与代码回滚分开评估，新增列通常可保留；禁止无评估地降级或删除新数据。

## 发布记录

每次正式发布必须更新：

- `CHANGELOG.md` 的版本、日期、变更和兼容性；
- `RELEASE.md` 中发生变化的操作要求；
- Git 标签、GitHub Release（若启用）及发行物；
- 部署文档与迁移文档。
