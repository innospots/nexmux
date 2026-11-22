/**
 * External-facing inbound APIs and adapters.
 *
 * <p>数据面（Runtime 进程）的唯一交付面。第一级为业务领域，领域内再按技术模块划分：
 * {@code invocation}（调用链路）、{@code health}（上游探活）、{@code catalog}（配置快照）、
 * {@code retention}（调用日志清理）、{@code runtime}（数据面生命周期编排）。</p>
 *
 * <p>约定：本包不持有持久化实体与 DAO，实体与数据真源位于
 * {@code com.innospots.nexmux.core.<domain>}；不依赖 {@code console} 包。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.inbound.invocation.endpoint.RuntimeIngressEndpoint
 */
package com.innospots.nexmux.inbound;
