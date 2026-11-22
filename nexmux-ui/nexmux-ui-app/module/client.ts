// 模块 client 入口：Shell 装配器静态 import "<pkg>/client"。
// 组件与动作和独立运行共用 src/frontend 注册表。
// 样式由 esbuild 抽成 client/index.css，Shell 通过 "<pkg>/styles.css" 引入。
//
// API 前缀必须与 pactor.module.json 的 id 一致：
// 打包器把 YAML 里的 /api/d/... 改成 /api/modules/nexmux/d/...，
// 这里让 TS 里写死的同一逻辑路径走同一挂载点。
import "../src/app/login.css";
import "../src/frontend/styles/console.css";

import { pactorActions } from "../src/frontend/actions";
import { pactorComponents } from "../src/frontend/components";
import { installModuleApiPrefix } from "../src/frontend/lib/api-base";

installModuleApiPrefix("/api/modules/nexmux");

export const extensions = {
  components: pactorComponents,
  actions: pactorActions,
};
