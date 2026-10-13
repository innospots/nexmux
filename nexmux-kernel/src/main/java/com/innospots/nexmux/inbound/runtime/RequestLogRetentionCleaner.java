package com.innospots.nexmux.inbound.runtime;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调用日志保留清理（G-05）：按服务设置 {@code logRetentionDays} 每日清理
 * {@code mux_request_log} 超期行。{@code mux_request_log} 仅 Runtime 进程写入
 * （ADR-0002 表级写责分离），清理任务同属 Runtime 职责；Console 调试台过渡期的
 * {@code source=DEBUG} 行同口径清理。
 *
 * <p>{@code logRetentionDays} ≤ 0 或非法值视为不清理（保守：缺省不误删）。
 */
public final class RequestLogRetentionCleaner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RequestLogRetentionCleaner.class);

    /** 首跑延迟（分钟）：避让启动高峰与首轮快照/探活。 */
    private static final long INITIAL_DELAY_MINUTES = 10;
    private static final long PERIOD_MINUTES = TimeUnit.DAYS.toMinutes(1);

    private final RequestLogDao requestLogDao;
    private final PlatformSettingService platformSettingService;
    private volatile boolean started;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "nexmux-request-log-retention");
                thread.setDaemon(true);
                return thread;
            });

    public RequestLogRetentionCleaner(RequestLogDao requestLogDao,
                                      PlatformSettingService platformSettingService) {
        this.requestLogDao = requestLogDao;
        this.platformSettingService = platformSettingService;
    }

    /** 启动周期清理（幂等）。 */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        executor.scheduleWithFixedDelay(this::cleanQuietly,
                INITIAL_DELAY_MINUTES, PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    private void cleanQuietly() {
        try {
            clean();
        } catch (RuntimeException e) {
            log.warn("request log retention cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 执行一轮清理。
     *
     * @return 删除行数；保留天数 ≤0 / 非法时返回 0（不清理）
     */
    int clean() {
        int days = retentionDays();
        if (days <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = requestLogDao.delete(new QueryWrapper<RequestLogEntity>()
                .lt("requested_at", cutoff));
        if (deleted > 0) {
            log.info("request log retention cleanup: deleted {} row(s) older than {} day(s) (cutoff {})",
                    deleted, days, cutoff);
        }
        return deleted;
    }

    private int retentionDays() {
        try {
            String value = platformSettingService.getAll().get("logRetentionDays");
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
