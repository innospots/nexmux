package com.innospots.nexmux.core.route.domain.value;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由规格（草稿或已发布）。
 *
 * @param alias                    路由别名
 * @param ingress                  入口标识
 * @param strategyType             负载策略
 * @param candidates               候选模型列表
 * @param filterUnhealthyBindings  是否过滤不健康绑定
 * @param schemaVersion            规格 schema 版本
 */
@Schema(name = "RouteSpec", description = "路由规格")
public record RouteSpec(
        @Schema(description = "路由别名")
        String alias,
        @Schema(description = "入口标识")
        String ingress,
        @Schema(description = "负载策略", examples = {"WEIGHTED"})
        String strategyType,
        @Schema(description = "候选模型列表")
        List<RouteCandidate> candidates,
        @Schema(description = "是否过滤不健康绑定")
        boolean filterUnhealthyBindings,
        @Schema(description = "规格 schema 版本", examples = {"1"})
        int schemaVersion) {

    public RouteSpec {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
