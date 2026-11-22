/**
 * 数据面生命周期领域：快照刷新、探活与配额校准的周期调度。
 *
 * <p>只做编排与生命周期管理，不含业务规则；生命周期由装配方（运行模块）启动与关闭。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see com.innospots.nexmux.inbound.runtime.RuntimeSnapshotRefresher
 */
package com.innospots.nexmux.inbound.runtime;
