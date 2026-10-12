package com.innospots.nexmux.inbound.runtime;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BindingHealthRegistryTest {

    private static final BindingHealthRegistry.Target TARGET =
            new BindingHealthRegistry.Target("pv-1", "o4-mini");

    private BindingHealthRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BindingHealthRegistry(3, 10000);
    }

    @Test
    void unknownTargetDefaultsHealthyAndSelectable() {
        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.HEALTHY);
        assertThat(registry.isSelectable(TARGET)).isTrue();
    }

    @Test
    void successKeepsHealthyAndResetsFailures() {
        registry.recordFailure(TARGET, "boom");
        registry.recordSuccess(TARGET, 100);

        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void slowSuccessMarksDegradedButSelectable() {
        registry.recordSuccess(TARGET, 15000);

        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.DEGRADED);
        assertThat(registry.isSelectable(TARGET)).isTrue();
    }

    @Test
    void consecutiveFailuresTripUnavailableAtThreshold() {
        registry.recordFailure(TARGET, "e1");
        registry.recordFailure(TARGET, "e2");
        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.HEALTHY);

        registry.recordFailure(TARGET, "e3");

        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.UNAVAILABLE);
        assertThat(registry.isSelectable(TARGET)).isFalse();
    }

    @Test
    void successRecoversFromUnavailable() {
        for (int i = 0; i < 3; i++) {
            registry.recordFailure(TARGET, "e");
        }
        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.UNAVAILABLE);

        registry.recordSuccess(TARGET, 100);

        assertThat(registry.statusOf(TARGET)).isEqualTo(HealthStatus.HEALTHY);
        assertThat(registry.isSelectable(TARGET)).isTrue();
    }

    @Test
    void snapshotCapturesStatesForPersistence() {
        registry.recordFailure(TARGET, "timeout");

        Map<BindingHealthRegistry.Target, BindingHealthRegistry.State> snapshot = registry.snapshot();

        assertThat(snapshot).containsKey(TARGET);
        assertThat(snapshot.get(TARGET).consecutiveFailures()).isEqualTo(1);
        assertThat(snapshot.get(TARGET).lastError()).isEqualTo("timeout");
        assertThat(snapshot.get(TARGET).lastProbeAt()).isNotNull();
    }
}
