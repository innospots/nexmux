package com.innospots.nexmux.core.model.domain.value;

import java.math.BigDecimal;

/**
 * 扩展绑定项（领域设计 §5.1）：序列化为 {@code mux_model.extra_bindings} JSON 数组元素。
 * 健康状态不在本结构内——由 Runtime 侧 {@code mux_binding_health} 承载（ADR-0001 §3）。
 *
 * @param providerId          供应商 ID
 * @param providerModelId     上游实际模型 ID
 * @param upstreamProtocol    可覆盖 Provider default_protocol（可空）
 * @param weight              Binding 权重（与同 Model 下其他 Binding 合计 100）
 * @param costMultiplier      成本倍率 C（可空 = 1）
 * @param referenceMultiplier 内部参考倍率 M（可空 = 1）
 * @param enabled             启用态（null = 启用）
 */
public record ModelBindingItem(
        String providerId,
        String providerModelId,
        String upstreamProtocol,
        Integer weight,
        BigDecimal costMultiplier,
        BigDecimal referenceMultiplier,
        Boolean enabled) {
}
