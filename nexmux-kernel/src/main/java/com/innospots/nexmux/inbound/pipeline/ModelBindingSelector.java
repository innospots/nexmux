package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.model.domain.value.ModelBindingItem;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.selection.WeightedPick;
import com.innospots.nexmux.inbound.runtime.BindingHealthRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Model 内 Binding 选择（PRD §31「Provider Binding Strategy」）：主绑定 + 扩展绑定，
 * 过滤停用与已知不健康（{@link BindingHealthRegistry}）后按权重加权选择。
 * 选中后失败不自动切换（P0；熔断与有界 Failover 为 P1）。
 */
public final class ModelBindingSelector {

    /**
     * 命中 Binding。
     *
     * @param providerId          供应商 ID
     * @param providerModelId     上游实际模型 ID
     * @param upstreamProtocol    上游协议（可空 = 继承 Provider default_protocol）
     * @param costMultiplier      成本倍率 C（null = 1）
     * @param referenceMultiplier 内部参考倍率 M（null = 1）
     * @param primary             是否主绑定
     */
    public record ResolvedBinding(String providerId, String providerModelId, String upstreamProtocol,
                                  BigDecimal costMultiplier, BigDecimal referenceMultiplier,
                                  boolean primary) {
    }

    private final BindingHealthRegistry healthRegistry;

    public ModelBindingSelector(BindingHealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
    }

    /** 选择一个可用 Binding（默认过滤已知不健康）；全部停用 / 不健康时返回 empty。 */
    public Optional<ResolvedBinding> select(ModelEntity model) {
        return select(model, true);
    }

    /**
     * 选择一个 Binding。
     *
     * @param filterUnhealthy 是否剔除 UNAVAILABLE 绑定（取路由快照的
     *                        {@code filterUnhealthyBindings}；false 时不过滤，
     *                        不健康绑定被选中后将真实调上游并失败——PRD §19.7
     *                        「实际调用失败后记录错误并结束请求」）
     */
    public Optional<ResolvedBinding> select(ModelEntity model, boolean filterUnhealthy) {
        java.util.stream.Stream<ResolvedBinding> pool = bindingsOf(model).stream();
        if (filterUnhealthy) {
            pool = pool.filter(b -> healthRegistry.isSelectable(
                    new BindingHealthRegistry.Target(b.providerId(), b.providerModelId())));
        }
        return WeightedPick.pick(pool.toList(), b -> weightOf(model, b));
    }

    /** 该 Model 是否还有可选 Binding（路由级健康过滤用）。 */
    public boolean hasSelectableBinding(ModelEntity model) {
        return bindingsOf(model).stream()
                .anyMatch(b -> healthRegistry.isSelectable(
                        new BindingHealthRegistry.Target(b.providerId(), b.providerModelId())));
    }

    /** 模型全部 Binding（主 + 扩展，已过滤停用）。 */
    public static List<ResolvedBinding> bindingsOf(ModelEntity model) {
        List<ResolvedBinding> out = new ArrayList<>();
        if (model.getPrimaryProviderId() != null
                && (model.getPrimaryBindingEnabled() == null || model.getPrimaryBindingEnabled())) {
            out.add(new ResolvedBinding(
                    model.getPrimaryProviderId(), model.getPrimaryProviderModelId(),
                    model.getPrimaryUpstreamProtocol(),
                    model.getPrimaryCostMultiplier(), model.getPrimaryReferenceMultiplier(), true));
        }
        for (ModelBindingItem item : extraBindingsOf(model)) {
            if (item.enabled() == null || item.enabled()) {
                out.add(new ResolvedBinding(
                        item.providerId(), item.providerModelId(), item.upstreamProtocol(),
                        item.costMultiplier(), item.referenceMultiplier(), false));
            }
        }
        return out;
    }

    /** 权重：主绑定缺省 100（仅主绑定时）；扩展绑定缺省 0。 */
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
        return 0;
    }

    private static List<ModelBindingItem> extraBindingsOf(ModelEntity model) {
        String json = model.getExtraBindings();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return NexmuxJson.read(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<ModelBindingItem>>() {});
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
