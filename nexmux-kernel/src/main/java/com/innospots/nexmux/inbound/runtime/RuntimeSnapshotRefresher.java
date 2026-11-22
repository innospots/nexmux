package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.health.service.BindingHealthProber;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据面快照调度器：周期性全量重载配置快照、驱动主动探活与配额校准。
 *
 * <p>单调度线程；单轮失败仅记日志并在下轮重试，避免把瞬时故障升级为进程退出。
 * 生命周期由装配方管理（运行模块启动时 {@link #start}，关闭时 {@link #close}）。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see PublishedRouteCache
 * @see RuntimeCatalogCache
 * @see BindingHealthProber
 */
@Slf4j
public final class RuntimeSnapshotRefresher implements AutoCloseable {

    private final PublishedRouteCache publishedRouteCache;
    private final RuntimeCatalogCache runtimeCatalogCache;
    private final BindingHealthProber bindingHealthProber;
    private final Runnable quotaResync;
    private volatile boolean started;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "nexmux-runtime-snapshot");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * @param publishedRouteCache  已发布路由缓存
     * @param runtimeCatalogCache  配置目录缓存
     * @param bindingHealthProber  绑定探活器
     * @param quotaResync          配额校准任务（可空表示不校准）
     */
    public RuntimeSnapshotRefresher(PublishedRouteCache publishedRouteCache,
                                    RuntimeCatalogCache runtimeCatalogCache,
                                    BindingHealthProber bindingHealthProber,
                                    Runnable quotaResync) {
        this.publishedRouteCache = publishedRouteCache;
        this.runtimeCatalogCache = runtimeCatalogCache;
        this.bindingHealthProber = bindingHealthProber;
        this.quotaResync = quotaResync;
    }

    /**
     * 启动周期任务（幂等）：先同步执行一轮（启动即可服务），再按各自周期调度。
     *
     * @param snapshotPollIntervalSeconds 快照重载周期（秒）
     * @param healthProbeIntervalSeconds  探活周期（秒）
     * @param quotaResyncIntervalSeconds  配额校准周期（秒）
     */
    public synchronized void start(long snapshotPollIntervalSeconds, long healthProbeIntervalSeconds,
                                   long quotaResyncIntervalSeconds) {
        if (started) {
            return;
        }
        started = true;
        refreshOnceQuietly();
        probeOnceQuietly();
        resyncQuotaQuietly();
        executor.scheduleWithFixedDelay(this::refreshOnceQuietly,
                snapshotPollIntervalSeconds, snapshotPollIntervalSeconds, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::probeOnceQuietly,
                healthProbeIntervalSeconds, healthProbeIntervalSeconds, TimeUnit.SECONDS);
        if (quotaResync != null) {
            executor.scheduleWithFixedDelay(this::resyncQuotaQuietly,
                    quotaResyncIntervalSeconds, quotaResyncIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    /** 立即重载配置快照（路由 + 目录）。 */
    public void refreshOnce() {
        runtimeCatalogCache.refresh();
        publishedRouteCache.refresh();
    }

    /** 立即探活一轮。 */
    public void probeOnce() {
        bindingHealthProber.probeAll();
    }

    private void resyncQuotaQuietly() {
        if (quotaResync == null) {
            return;
        }
        try {
            quotaResync.run();
        } catch (RuntimeException e) {
            log.warn("quota resync failed: {}", e.getMessage());
        }
    }

    private void refreshOnceQuietly() {
        try {
            refreshOnce();
        } catch (RuntimeException e) {
            log.warn("runtime snapshot refresh failed, keep previous snapshot: {}", e.getMessage());
        }
    }

    private void probeOnceQuietly() {
        try {
            probeOnce();
        } catch (RuntimeException e) {
            log.warn("binding health probe round failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
