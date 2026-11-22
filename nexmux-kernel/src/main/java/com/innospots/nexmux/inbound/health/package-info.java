/**
 * 上游探活领域：按周期探测供应商可用性并写入健康状态。
 *
 * <p>健康状态机与持久化真源位于 {@code com.innospots.nexmux.core.health}；
 * 本包只承担出站探测编排（网络副作用）。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.inbound.health.service.BindingHealthProber
 */
package com.innospots.nexmux.inbound.health;
