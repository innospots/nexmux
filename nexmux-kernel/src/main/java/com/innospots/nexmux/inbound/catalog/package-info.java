/**
 * 运行配置快照领域：已发布路由与模型目录的只读内存视图。
 *
 * <p>请求链路不逐次查库；快照由 {@code inbound.runtime} 的
 * {@link com.innospots.nexmux.inbound.runtime.RuntimeSnapshotRefresher} 周期性全量重载。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache
 */
package com.innospots.nexmux.inbound.catalog;
