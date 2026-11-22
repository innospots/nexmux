/**
 * Binding 健康领域：上游可用性的持久化真源与状态机。
 *
 * <p>本域拥有 {@code mux_binding_health} 表（探活结果的低频落库）与健康状态迁移规则；
 * 主动探测（出站 HTTP）属于数据面编排，位于 {@code inbound.health.service}，
 * 只通过本域的 operator / service 写入，不直接访问 DAO。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.core.health.domain.entity.BindingHealthEntity
 * @see com.innospots.nexmux.inbound.health.service.BindingHealthProber
 */
package com.innospots.nexmux.core.health;
