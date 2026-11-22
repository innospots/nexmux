package com.innospots.nexmux.inbound.invocation.service;

import com.innospots.nexmux.core.health.service.BindingHealthRegistry;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.model.domain.value.ModelBindingItem;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.selection.WeightedPick;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Model 内 Binding 选择：主绑定 + 扩展绑定，过滤停用与已知不健康后按权重挑选。
 *
 * <p>选中后失败不自动切换（P0 语义；熔断与有界 failover 属后续能力）。健康判定委托
 * {@link BindingHealthRegistry}，本类不维护状态。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see ModelEntity
 * @see BindingHealthRegistry
 */
@Slf4j
@RequiredArgsConstructor
public final class ModelBindingResolver {

    /**
     * 命中 Binding。
     *
     * @param providerId          供应商 ID
     * @param providerModelId     上游实际模型 ID
     * @param upstreamProtocol    上游协议（可空 = 继承 Provider 默认协议）
     * @param costMultiplier      成本倍率 C（{@code null} 视为 1）
     * @param referenceMultiplier 内部参考倍率 M（{@code null} 视为 1）
     * @param primary             是否主绑定
     */
    public record ResolvedBinding(String providerId, String providerModelId, String upstreamProtocol,
                                  BigDecimal costMultiplier, BigDecimal referenceMultiplier,
                                  boolean primary) {
    }

    private final BindingHealthRegistry bindingHealthRegistry;

    /**
     * 选择一个可用 Binding（过滤已知不健康）。
     *
     * @param model 目标模型
     * @return 选中 Binding；全部停用或不健康时为空
     */
    public Optional<ResolvedBinding> select(ModelEntity model) {
        return select(model, true);
    }

    /**
     * 选择一个 Binding。
     *
     * @param model           目标模型
     * @param filterUnhealthy 是否剔除不可用绑定（取路由快照的 {@code filterUnhealthyBindings}；
     *                        {@code false} 时不过滤，不健康绑定被选中后将真实调上游并失败）
     * @return 选中 Binding；无可选时为空
     */
    public Optional<ResolvedBinding> select(ModelEntity model, boolean filterUnhealthy) {
        java.util.stream.Stream<ResolvedBinding> pool = bindingsOf(model).stream();
        if (filterUnhealthy) {
            pool = pool.filter(binding -> bindingHealthRegistry.isSelectable(
                    new BindingHealthRegistry.Target(binding.providerId(), binding.providerModelId())));
        }
        return WeightedPick.pick(pool.toList(), binding -> weightOf(model, binding));
    }

    /**
     * 该模型是否还有可选 Binding（路由级健康预过滤用）。
     *
     * @param model 目标模型
     * @return 存在可选绑定时为 {@code true}
     */
    public boolean hasSelectableBinding(ModelEntity model) {
        return bindingsOf(model).stream()
                .anyMatch(binding -> bindingHealthRegistry.isSelectable(
                        new BindingHealthRegistry.Target(binding.providerId(), binding.providerModelId())));
    }

    /**
     * 模型全部 Binding（主 + 扩展，已过滤停用项）。
     *
     * @param model 目标模型
     * @return 绑定列表
     */
    public static List<ResolvedBinding> bindingsOf(ModelEntity model) {
        List<ResolvedBinding> bindings = new ArrayList<>();
        if (model.getPrimaryProviderId() != null
                && (model.getPrimaryBindingEnabled() == null || model.getPrimaryBindingEnabled())) {
            bindings.add(new ResolvedBinding(
                    model.getPrimaryProviderId(), model.getPrimaryProviderModelId(),
                    model.getPrimaryUpstreamProtocol(),
                    model.getPrimaryCostMultiplier(), model.getPrimaryReferenceMultiplier(), true));
        }
        for (ModelBindingItem item : extraBindingsOf(model)) {
            if (item.enabled() == null || item.enabled()) {
                bindings.add(new ResolvedBinding(
                        item.providerId(), item.providerModelId(), item.upstreamProtocol(),
                        item.costMultiplier(), item.referenceMultiplier(), false));
            }
        }
        return bindings;
    }

    /**
     * 候选行的 C/M 倍率覆盖 Binding 默认倍率（留空继承原值）。
     *
     * @param binding   已选绑定
     * @param candidate 路由候选行
     * @return 覆盖后的绑定（无覆盖时原样返回）
     */
    public static ResolvedBinding withCandidateRates(ResolvedBinding binding, RouteCandidate candidate) {
        if (candidate.costRate() == null && candidate.billingRate() == null) {
            return binding;
        }
        return new ResolvedBinding(
                binding.providerId(),
                binding.providerModelId(),
                binding.upstreamProtocol(),
                candidate.costRate() != null ? candidate.costRate() : binding.costMultiplier(),
                candidate.billingRate() != null ? candidate.billingRate() : binding.referenceMultiplier(),
                binding.primary());
    }

    /**
     * 路由级健康预过滤：无可用 Binding 的候选模型视为不可用。
     *
     * @param spec       路由快照
     * @param modelLookup 模型编码 → 模型实体的解析函数
     * @return 应剔除的模型编码
     */
    public java.util.Set<String> unhealthyModelCodes(
            RouteSpec spec, java.util.function.Function<String, Optional<ModelEntity>> modelLookup) {
        if (!spec.filterUnhealthyBindings()) {
            return java.util.Set.of();
        }
        java.util.Set<String> unhealthy = new java.util.HashSet<>();
        for (RouteCandidate candidate : spec.candidates()) {
            if (candidate.enabled() != null && !candidate.enabled()) {
                continue;
            }
            boolean selectable = modelLookup.apply(candidate.modelCode())
                    .map(this::hasSelectableBinding)
                    .orElse(false);
            if (!selectable) {
                unhealthy.add(candidate.modelCode());
            }
        }
        return unhealthy;
    }

    /** 权重：主绑定缺省 100；扩展绑定缺省 0。 */
    private static int weightOf(ModelEntity model, ResolvedBinding binding) {
        if (binding.primary()) {
            return model.getPrimaryBindingWeight() == null ? 100
                    : Math.max(model.getPrimaryBindingWeight(), 0);
        }
        for (ModelBindingItem item : extraBindingsOf(model)) {
            if (item.providerId().equals(binding.providerId())
                    && java.util.Objects.equals(item.providerModelId(), binding.providerModelId())) {
                return item.weight() == null ? 0 : Math.max(item.weight(), 0);
            }
        }
        log.debug("binding weight not found, fallback to 0: {}", binding.providerModelId());
        return 0;
    }

    private static List<ModelBindingItem> extraBindingsOf(ModelEntity model) {
        String json = model.getExtraBindings();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return NexmuxJson.read(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<ModelBindingItem>>() {
                    });
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
