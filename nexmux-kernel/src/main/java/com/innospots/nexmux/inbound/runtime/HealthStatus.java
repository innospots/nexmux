package com.innospots.nexmux.inbound.runtime;

/**
 * Binding 健康状态（PRD §27）。语义迁移见 nexmux-runtime-design §6.2。
 */
public enum HealthStatus {

    /** 探测正常。 */
    HEALTHY,
    /** 可用但探测延迟超过 degraded 阈值。 */
    DEGRADED,
    /** 连续失败达到阈值，选路时剔除。 */
    UNAVAILABLE
}
