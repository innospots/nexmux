package com.innospots.nexmux.core.settings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.settings.dao.PlatformSettingDao;
import com.innospots.nexmux.core.settings.domain.entity.PlatformSettingEntity;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 平台服务设置（mux_platform_setting 键值表）：仅承认 {@link #DEFAULTS}
 * 中的键，未落库的键返回默认值；保存为逐键 upsert 并写配置审计。
 */
@RequiredArgsConstructor
public class PlatformSettingService {

    /** 已知设置键与默认值（与 Console 服务设置页契约一致，顺序即展示顺序）。 */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>() {{
        put("timezone", "Asia/Shanghai");
        put("language", "zh-CN");
        put("displayName", "Nexmux Model Console");
        put("currency", "USD");
        put("currencyDecimals", "4");
        put("tokenDisplay", "compact");
        put("dateFormat", "YYYY-MM-DD");
        put("timeFormat", "HH:mm:ss");
        put("promptPolicy", "masked");
        put("debugInvokeEnabled", "true");
        put("debugDailyQuota", "200");
        put("logRetentionDays", "90");
        put("requestTimeoutMs", "60000");
        put("upstreamConnectTimeoutMs", "10000");
    }};

    private final PlatformSettingDao platformSettingDao;
    private final ConfigAuditService configAuditService;

    /** 设置快照缓存：TTL 内免查库（Runtime 热路径单次调用会多次 getAll）；save 立即失效。 */
    private static final long CACHE_TTL_MS = 3000;

    private final Object cacheLock = new Object();
    private Map<String, String> cached;
    private long cachedAtMs;

    /** 全量设置（默认值 + 已落库覆盖），键序固定。TTL 缓存，返回防御性副本。 */
    public Map<String, String> getAll() {
        synchronized (cacheLock) {
            long now = System.currentTimeMillis();
            if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
                return new LinkedHashMap<>(cached);
            }
            Map<String, String> merged = loadAll();
            cached = merged;
            cachedAtMs = now;
            return new LinkedHashMap<>(merged);
        }
    }

    private Map<String, String> loadAll() {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULTS);
        List<PlatformSettingEntity> rows = platformSettingDao.selectList(
                new LambdaQueryWrapper<>());
        for (PlatformSettingEntity row : rows) {
            if (merged.containsKey(row.getSettingKey()) && row.getSettingValue() != null) {
                merged.put(row.getSettingKey(), row.getSettingValue());
            }
        }
        return merged;
    }

    /**
     * 保存（仅已知键，未知键忽略；null 值视为恢复默认即删除落库行），
     * 并写配置审计（before/after 快照）。
     */
    public Map<String, String> save(Map<String, String> values) {
        Map<String, String> before = getAll();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                if (!DEFAULTS.containsKey(key)) {
                    continue;
                }
                if (entry.getValue() == null) {
                    platformSettingDao.deleteById(key);
                } else {
                    upsert(key, entry.getValue());
                }
            }
        }
        synchronized (cacheLock) {
            cached = null;
        }
        Map<String, String> after = getAll();
        if (!before.equals(after)) {
            configAuditService.record(
                    "SETTINGS", "global", "UPDATE",
                    "更新服务设置",
                    NexmuxJson.write(Map.of("before", before, "after", after)));
        }
        return after;
    }

    private void upsert(String key, String value) {
        PlatformSettingEntity entity = new PlatformSettingEntity();
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        if (platformSettingDao.selectById(key) == null) {
            platformSettingDao.insert(entity);
        } else {
            platformSettingDao.updateById(entity);
        }
    }
}
