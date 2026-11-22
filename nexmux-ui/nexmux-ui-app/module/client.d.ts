import type { ComponentType } from "react";
import type { ActionHandler } from "@pactor-app/runtime";

/** 供 Shell 类型检查。运行时实现是 dist-module/client/index.js。 */
export const extensions: {
  components: Record<string, ComponentType<unknown>>;
  actions: Record<string, ActionHandler>;
};
