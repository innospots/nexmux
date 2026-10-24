package com.innospots.nexmux.core.route.domain.value;

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * 路由候选模型与权重。
 *
 * @param modelCode   模型编码
 * @param weight      权重
 * @param enabled     启用态（null = 启用；Console 扩展）
 * @param priority    优先级（P0 / P1 / P2，可空）
 * @param costRate    供应成本倍率 C（null = 继承 Model 配置）
 * @param billingRate 用户计费倍率 M（null = 继承）
 */
@Schema(name = "RouteCandidate", description = "路由候选模型与权重")
public record RouteCandidate(
        @Schema(description = "模型编码", required = true)
        String modelCode,
        @Schema(description = "权重", required = true, examples = {"100"})
        int weight,
        @Schema(description = "启用态")
        Boolean enabled,
        @Schema(description = "优先级", examples = {"P0"})
        String priority,
        @Schema(description = "供应成本倍率 C")
        BigDecimal costRate,
        @Schema(description = "用户计费倍率 M")
        BigDecimal billingRate) {

    /** 兼容构造器（Runtime 既有 2 参用法）。 */
    public RouteCandidate(String modelCode, int weight) {
        this(modelCode, weight, null, null, null, null);
    }
}
