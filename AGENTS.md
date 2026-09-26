# Agent 操作指南

Nexmux 外部产品工程。平台能力通过 **`innospots-nexus-*` Maven 依赖**挂接，不在本仓库复制 Nexus 模块树。

## 已确认模块（仅此四项 Maven + UI，勿增删）

| 目录 | Maven | 说明 |
|------|-------|------|
| `nexmux-bom` | 是 | `<parent>` = `innospots-nexus-bom` |
| `nexmux-kernel` | 是 | 产品内核（`core` / `console` / `inbound` 包） |
| `nexmux-spring` | 是 | Spring Boot 可运行组装（assembly 发行包） |
| `nexmux-ui` | 否 | 前端 |

- 根 **`nexmux/pom.xml`** 直接 `<parent>` = **`innospots-nexus-parent`**，并在 **`dependencyManagement`** 中 import **`nexmux-bom`**。
- **`nexmux-bom`**：`<parent>` = **`innospots-nexus-bom`**（`relativePath` → sibling `../innospots-nexus/innospots-nexus-bom`；`<version>` 写死 `0.1.0-SNAPSHOT`），在 `dependencyManagement` 登记本产品 artifact；**不再** import `innospots-nexus-bom`。
- **`kernel` / `spring`** 仅 `<parent>` = 根 **`nexmux`**（`../pom.xml`），不得再直连 `innospots-nexus-parent` 或重复 import BOM。
- 模块 POM 禁止内联 JAR `<version>`。

## 产品内依赖

- **`nexmux-kernel`** 依赖 **`innospots-nexus-platform`**（纯平台内核，无 Spring 装配）；包结构：`com.innospots.nexmux.core`、`console`、`inbound`。
- **`nexmux-spring`** 依赖 **`nexmux-kernel`** + **`innospots-nexus-spring-platform`** + **H2（runtime）**；`mvn package` 产出 **`dist/nexmux-spring.tar.gz`**（脚本与 assembly 描述在 **`nexmux-spring/bin/`**）。

## 验证

```bash
cd ../innospots-nexus && mvn clean install -DskipTests
cd ../nexmux && mvn validate clean compile
cd ../nexmux && mvn package -pl nexmux-spring -am -DskipTests
```
