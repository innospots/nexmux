package com.innospots.nexmux.inbound.runtime;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binding 健康视图（Runtime 进程内，PRD §27 + nexmux-runtime-design §6.2）。
 *
 * <p>状态机：成功 → 按延迟判 HEALTHY / DEGRADED；连续失败达阈值 → UNAVAILABLE；
 * 任一成功立即恢复（失败计数清零）。未探测目标默认 HEALTHY（P0 宽松语义）。
 * 持久化由 {@link BindingHealthProber} 以 {@link #snapshot()} 低频 upsert
 * {@code mux_binding_health}，不回写配置表（ADR-0001 §3）。
 */
public final class BindingHealthRegistry {

    /** 探测目标：供应商 + 上游模型。 */
    public record Target(String providerId, String providerModelId) {
    }

    /** 单目标状态（不可变快照）。 */
    public record State(HealthStatus status, int consecutiveFailures, Long lastLatencyMs,
                        String lastError, LocalDateTime lastProbeAt) {
    }

    private final int unhealthyThreshold;
    private final long degradedLatencyMs;
    private final Map<Target, State> states = new ConcurrentHashMap<>();

    public BindingHealthRegistry(int unhealthyThreshold, long degradedLatencyMs) {
        this.unhealthyThreshold = unhealthyThreshold;
        this.degradedLatencyMs = degradedLatencyMs;
    }

    /** 探测成功：失败计数清零，按延迟判 HEALTHY / DEGRADED。 */
    public HealthStatus recordSuccess(Target target, long latencyMs) {
        HealthStatus status = latencyMs > degradedLatencyMs ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
        states.put(target, new State(status, 0, latencyMs, null, LocalDateTime.now()));
        return status;
    }

    /** 探测失败：连续失败达阈值判 UNAVAILABLE，否则保持可用。 */
    public HealthStatus recordFailure(Target target, String error) {
        State previous = states.get(target);
        int failures = (previous == null ? 0 : previous.consecutiveFailures()) + 1;
        HealthStatus status = failures >= unhealthyThreshold
                ? HealthStatus.UNAVAILABLE
                : (previous != null && previous.status() == HealthStatus.DEGRADED
                        ? HealthStatus.DEGRADED : HealthStatus.HEALTHY);
        states.put(target, new State(status, failures,
                previous == null ? null : previous.lastLatencyMs(), error, LocalDateTime.now()));
        return status;
    }

    /** 当前状态（未探测目标默认 HEALTHY）。 */
    public HealthStatus statusOf(Target target) {
        State state = states.get(target);
        return state == null ? HealthStatus.HEALTHY : state.status();
    }

    /** 是否参与选路（UNAVAILABLE 剔除，DEGRADED 保留）。 */
    public boolean isSelectable(Target target) {
        return statusOf(target) != HealthStatus.UNAVAILABLE;
    }

    /** 状态快照（持久化用；返回副本）。 */
    public Map<Target, State> snapshot() {
        return Map.copyOf(states);
    }
}
