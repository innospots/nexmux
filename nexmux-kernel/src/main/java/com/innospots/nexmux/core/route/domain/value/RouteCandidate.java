package com.innospots.nexmux.core.route.domain.value;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由候选模型与权重。
 *
 * @param modelCode 模型编码
 * @param weight    权重
 */
@Schema(name = "RouteCandidate", description = "路由候选模型与权重")
public record RouteCandidate(
        @Schema(description = "模型编码", required = true)
        String modelCode,
        @Schema(description = "权重", required = true, examples = {"100"})
        int weight) {
}
