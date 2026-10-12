package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.inbound.runtime.BindingHealthRegistry;
import com.innospots.nexmux.inbound.runtime.HealthStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelBindingSelectorTest {

    private BindingHealthRegistry registry;
    private ModelBindingSelector selector;

    @BeforeEach
    void setUp() {
        registry = new BindingHealthRegistry(3, 10000);
        selector = new ModelBindingSelector(registry);
    }

    private static ModelEntity primaryOnlyModel() {
        ModelEntity model = new ModelEntity();
        model.setModelCode("openai/o4-mini");
        model.setPrimaryProviderId("pv-1");
        model.setPrimaryProviderModelId("o4-mini");
        model.setPrimaryBindingWeight(100);
        return model;
    }

    @Test
    void selectsPrimaryBindingByDefault() {
        Optional<ModelBindingSelector.ResolvedBinding> selected = selector.select(primaryOnlyModel());

        assertThat(selected).isPresent();
        assertThat(selected.get().primary()).isTrue();
        assertThat(selected.get().providerId()).isEqualTo("pv-1");
        assertThat(selected.get().providerModelId()).isEqualTo("o4-mini");
    }

    @Test
    void emptyWhenPrimaryDisabledAndNoExtras() {
        ModelEntity model = primaryOnlyModel();
        model.setPrimaryBindingEnabled(false);

        assertThat(selector.select(model)).isEmpty();
        assertThat(selector.hasSelectableBinding(model)).isFalse();
    }

    @Test
    void unhealthyBindingIsExcluded() {
        ModelEntity model = primaryOnlyModel();
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");

        assertThat(selector.select(model)).isEmpty();
    }

    @Test
    void filterDisabledReturnsUnhealthyBinding() {
        // filterUnhealthyBindings=false：不健康绑定保留在池内（调用将真实失败，PRD §19.7）
        ModelEntity model = primaryOnlyModel();
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");

        assertThat(selector.select(model, false)).isPresent();
        assertThat(selector.select(model, false).get().providerId()).isEqualTo("pv-1");
    }

    @Test
    void degradedBindingRemainsSelectable() {
        ModelEntity model = primaryOnlyModel();
        registry.recordSuccess(new BindingHealthRegistry.Target("pv-1", "o4-mini"), 20000);
        assertThat(registry.statusOf(new BindingHealthRegistry.Target("pv-1", "o4-mini")))
                .isEqualTo(HealthStatus.DEGRADED);

        assertThat(selector.select(model)).isPresent();
    }

    @Test
    void fallsBackToExtraBindingWhenPrimaryUnhealthy() {
        ModelEntity model = primaryOnlyModel();
        model.setExtraBindings("[{\"providerId\":\"pv-2\",\"providerModelId\":\"backup-mini\","
                + "\"weight\":100,\"costMultiplier\":1.5}]");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");
        registry.recordFailure(new BindingHealthRegistry.Target("pv-1", "o4-mini"), "e");

        Optional<ModelBindingSelector.ResolvedBinding> selected = selector.select(model);

        assertThat(selected).isPresent();
        assertThat(selected.get().primary()).isFalse();
        assertThat(selected.get().providerId()).isEqualTo("pv-2");
        assertThat(selected.get().costMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    void malformedExtraBindingsToleratedAsEmpty() {
        ModelEntity model = primaryOnlyModel();
        model.setExtraBindings("{not-json");

        assertThat(selector.select(model)).isPresent();
    }
}
