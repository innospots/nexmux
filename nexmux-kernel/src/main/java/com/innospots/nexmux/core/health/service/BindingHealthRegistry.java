package com.innospots.nexmux.core.health.service;

import com.innospots.nexmux.core.health.domain.enums.HealthStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binding 健康状态机（进程内视图）。
 *
 * <p>成功 → 按延迟判 {@link HealthStatus#HEALTHY} / {@link HealthStatus#DEGRADED}；
 * 连续失败达阈值 → {@link HealthStatus#UNAVAILABLE}；任一成功立即恢复（失败计数清零）。
 * 未探测目标默认健康（宽松语义，避免新配置被误判不可用）。</p>
 *
 * <p>本类只维护状态，不触达网络与数据库：由数据面探测服务调用
 * {@code recordSuccess} / {@code recordFailure}，并自行决定落库时机。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see HealthStatus
 * @see com.innospots.nexmux.inbound.health.service.BindingHealthProber
 */
public final class BindingHealthRegistry {

    /**
     * 探测目标：供应商 + 上游模型。
     *
     * @param providerId      供应商 ID
     * @param providerModelId 上游模型 ID
     */
    public record Target(String providerId, String providerModelId) {
    }

    /**
     * 单目标状态快照。
     *
     * @param status              当前健康状态
     * @param consecutiveFailures 连续失败次数
     * @param lastLatencyMs       最近探测延迟（毫秒，可空）
     * @param lastError           最近错误（可空）
     * @param lastProbeAt         最近探测时间
     */
    public record State(HealthStatus status, int consecutiveFailures, Long lastLatencyMs,
                        String lastError, LocalDateTime lastProbeAt) {
    }

    private final int unhealthyThreshold;
    private final long degradedLatencyMs;
    private final Map<Target, State> states = new ConcurrentHashMap<>();

    /**
     * @param unhealthyThreshold 连续失败达该次数判定不可用
     * @param degradedLatencyMs  延迟超过该毫秒数判定降级
     */
    public BindingHealthRegistry(int unhealthyThreshold, long degradedLatencyMs) {
        this.unhealthyThreshold = unhealthyThreshold;
        this.degradedLatencyMs = degradedLatencyMs;
    }

    /**
     * 记录一次探测成功。
     *
     * @param target    探测目标
     * @param latencyMs 探测延迟（毫秒）
     * @return 迁移后的状态
     */
    public HealthStatus recordSuccess(Target target, long latencyMs) {
        HealthStatus status = latencyMs > degradedLatencyMs
                ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
        states.put(target, new State(status, 0, latencyMs, null, LocalDateTime.now()));
        return status;
    }

    /**
     * 记录一次探测失败。
     *
     * @param target 探测目标
     * @param error  失败原因
     * @return 迁移后的状态
     */
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

    /**
     * 查询当前状态。
     *
     * @param target 探测目标
     * @return 状态；未探测目标返回 {@link HealthStatus#HEALTHY}
     */
    public HealthStatus statusOf(Target target) {
        State state = states.get(target);
        return state == null ? HealthStatus.HEALTHY : state.status();
    }

    /**
     * 查询指定目标的状态快照。
     *
     * @param target 探测目标
     * @return 状态快照；未探测时为空
     */
    public Optional<State> stateOf(Target target) {
        return Optional.ofNullable(states.get(target));
    }

    /**
     * 是否参与选路（仅 {@link HealthStatus#UNAVAILABLE} 被剔除，降级保留）。
     *
     * @param target 探测目标
     * @return 可选路时为 {@code true}
     */
    public boolean isSelectable(Target target) {
        return statusOf(target) != HealthStatus.UNAVAILABLE;
    }

    /**
     * 全量状态副本（落库用）。
     *
     * @return 不可变快照
     */
    public Map<Target, State> snapshot() {
        return Map.copyOf(states);
    }
}
