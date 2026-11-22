import type { ComponentType } from "react";

/**
 * 供 Shell 类型检查。不用 @pactor-app/runtime 的 ActionHandler，
 * 避免不同依赖树各解析一份 runtime 时出现名义类型冲突。
 * 运行时实现是 dist-module/client/index.js。
 */
export const extensions: {
  components: Record<string, ComponentType<unknown>>;
  actions: Record<string, never>;
};
