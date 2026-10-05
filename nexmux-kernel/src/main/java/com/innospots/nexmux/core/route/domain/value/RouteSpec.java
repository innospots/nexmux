package com.innospots.nexmux.core.route.domain.value;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由规格（草稿或已发布）。
 *
 * @param alias                    路由别名
 * @param ingress                  入口标识（请求路径，如 /v1/chat/completions）
 * @param strategyType             负载策略
 * @param candidates               候选模型列表
 * @param filterUnhealthyBindings  是否过滤不健康绑定
 * @param schemaVersion            规格 schema 版本
 * @param description              描述（Console 扩展，schema v1 向后兼容）
 * @param protocol                 协议格式（如 OpenAI / Chat Completions）
 * @param tags                     标签
 * @param stickySession            粘性 Session（同会话固定同一模型实例）
 */
@Schema(name = "RouteSpec", description = "路由规格")
public record RouteSpec(
        @Schema(description = "路由别名")
        String alias,
        @Schema(description = "入口标识（请求路径）")
        String ingress,
        @Schema(description = "负载策略", examples = {"WEIGHTED"})
        String strategyType,
        @Schema(description = "候选模型列表")
        List<RouteCandidate> candidates,
        @Schema(description = "是否过滤不健康绑定")
        boolean filterUnhealthyBindings,
        @Schema(description = "规格 schema 版本", examples = {"1"})
        int schemaVersion,
        @Schema(description = "描述")
        String description,
        @Schema(description = "协议格式")
        String protocol,
        @Schema(description = "标签")
        List<String> tags,
        @Schema(description = "粘性 Session")
        Boolean stickySession) {

    /** 兼容构造器（Runtime 既有 6 参用法）。 */
    public RouteSpec(String alias, String ingress, String strategyType,
                     List<RouteCandidate> candidates, boolean filterUnhealthyBindings, int schemaVersion) {
        this(alias, ingress, strategyType, candidates, filterUnhealthyBindings, schemaVersion,
                null, null, List.of(), null);
    }

    public RouteSpec {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
