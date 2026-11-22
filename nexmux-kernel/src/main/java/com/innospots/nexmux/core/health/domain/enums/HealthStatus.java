package com.innospots.nexmux.core.health.domain.enums;

/**
 * Binding 健康状态。
 *
 * <p>迁移规则见 {@link com.innospots.nexmux.core.health.service.BindingHealthRegistry}：
 * 探测成功按延迟判定 {@link #HEALTHY} / {@link #DEGRADED}，连续失败达阈值判 {@link #UNAVAILABLE}。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.core.health.domain.entity.BindingHealthEntity
 */
public enum HealthStatus {

    /** 探测正常。 */
    HEALTHY,

    /** 可用但探测延迟超过 degraded 阈值。 */
    DEGRADED,

    /** 连续失败达到阈值，选路时剔除。 */
    UNAVAILABLE
}
