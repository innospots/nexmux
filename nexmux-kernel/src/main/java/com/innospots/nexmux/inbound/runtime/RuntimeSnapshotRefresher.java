package com.innospots.nexmux.inbound.runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime 快照调度器（nexmux-runtime-design §5.3/§6.4）：周期性全量重载配置快照
 * （路由 + 目录）并驱动主动探活。单调度线程；单轮失败仅记日志，下轮重试。
 * 生命周期由装配方管理（runtime profile 启动时 {@link #start}，关闭时 {@link #close}）。
 */
public final class RuntimeSnapshotRefresher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSnapshotRefresher.class);

    private final PublishedRouteCache routeCache;
    private final RuntimeCatalogCache catalogCache;
    private final BindingHealthProber healthProber;
    private final Runnable quotaResync;
    private volatile boolean started;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "nexmux-runtime-snapshot");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * @param quotaResync 配额库校准任务（可空 = 不校准）；通常 {@code pipeline::resyncQuotas}
     */
    public RuntimeSnapshotRefresher(PublishedRouteCache routeCache, RuntimeCatalogCache catalogCache,
                                    BindingHealthProber healthProber, Runnable quotaResync) {
        this.routeCache = routeCache;
        this.catalogCache = catalogCache;
        this.healthProber = healthProber;
        this.quotaResync = quotaResync;
    }

    /** 启动周期任务（幂等）：先同步执行一轮（启动即可服务），再按各自周期调度。 */
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
        catalogCache.refresh();
        routeCache.refresh();
    }

    /** 立即探活一轮。 */
    public void probeOnce() {
        healthProber.probeAll();
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
