package com.innospots.nexmux.core.route.domain.request;

import java.util.List;

import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 创建路由。
 *
 * @param routeCode    路由编码（稳定业务键，URL 用）
 * @param alias        路由别名（对外逻辑模型名）
 * @param displayName  显示名称
 * @param description  描述
 * @param path         请求路径（如 /v1/chat/completions）
 * @param protocol     协议格式
 * @param strategyType 负载策略（FIXED / WEIGHTED / ROUND_ROBIN / PRIORITY / HASH / RANDOM）
 * @param stickySession 粘性 Session
 * @param healthFilter 仅调度健康实例
 * @param tags         标签
 * @param models       候选模型行
 */
@Schema(name = "RouteCreateRequest", description = "创建路由")
public record RouteCreateRequest(
        @Schema(description = "路由编码", required = true, examples = {"general-chat"})
        String routeCode,
        @Schema(description = "路由别名", required = true, examples = {"general"})
        String alias,
        @Schema(description = "显示名称", required = true)
        String displayName,
        @Schema(description = "描述")
        String description,
        @Schema(description = "请求路径", examples = {"/v1/chat/completions"})
        String path,
        @Schema(description = "协议格式", examples = {"OpenAI / Chat Completions"})
        String protocol,
        @Schema(description = "负载策略", examples = {"FIXED"})
        String strategyType,
        @Schema(description = "粘性 Session")
        Boolean stickySession,
        @Schema(description = "仅调度健康实例")
        Boolean healthFilter,
        @Schema(description = "标签")
        List<String> tags,
        @Schema(description = "候选模型行")
        List<RouteCandidate> models) {
}
