package com.innospots.nexmux.core.settings.service;

import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.settings.dao.PlatformSettingDao;
import com.innospots.nexmux.core.settings.domain.entity.PlatformSettingEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PlatformSettingServiceTest {

    private PlatformSettingDao platformSettingDao;
    private ConfigAuditService configAuditService;
    private PlatformSettingService platformSettingService;

    @BeforeEach
    void setUp() {
        platformSettingDao = Mockito.mock(PlatformSettingDao.class);
        configAuditService = Mockito.mock(ConfigAuditService.class);
        platformSettingService = new PlatformSettingService(platformSettingDao, configAuditService);
    }

    private static PlatformSettingEntity row(String key, String value) {
        PlatformSettingEntity entity = new PlatformSettingEntity();
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        return entity;
    }

    @Test
    void getAllReturnsDefaultsWhenTableEmpty() {
        when(platformSettingDao.selectList(any())).thenReturn(List.of());

        Map<String, String> all = platformSettingService.getAll();

        assertThat(all).isEqualTo(PlatformSettingService.DEFAULTS);
        assertThat(all.get("timezone")).isEqualTo("Asia/Shanghai");
        assertThat(all.get("requestTimeoutMs")).isEqualTo("60000");
    }

    @Test
    void getAllCoalescesReadsWithinTtlAndSaveInvalidates() {
        // TTL 缓存：窗口内重复 getAll 只查一次库（Runtime 热路径每次调用多次 getAll）；
        // save 后立即失效，下一次 getAll 读到新值
        when(platformSettingDao.selectList(any())).thenReturn(List.of());

        platformSettingService.getAll();
        platformSettingService.getAll();
        Mockito.verify(platformSettingDao, Mockito.times(1)).selectList(any());

        when(platformSettingDao.selectList(any())).thenReturn(List.of(row("currency", "CNY")));
        platformSettingService.save(Map.of("currency", "CNY"));

        assertThat(platformSettingService.getAll().get("currency")).isEqualTo("CNY");
    }

    @Test
    void getAllOverlaysStoredValues() {
        when(platformSettingDao.selectList(any())).thenReturn(List.of(
                row("currency", "CNY"), row("unknown-key", "x")));

        Map<String, String> all = platformSettingService.getAll();

        assertThat(all.get("currency")).isEqualTo("CNY");
        assertThat(all).doesNotContainKey("unknown-key");
        assertThat(all.get("language")).isEqualTo("zh-CN");
    }

    @Test
    void savePersistsKnownKeysAndWritesAudit() {
        when(platformSettingDao.selectList(any()))
                .thenReturn(List.of(), List.of(row("currency", "CNY")));
        when(platformSettingDao.selectById("currency")).thenReturn(null);

        Map<String, String> after = platformSettingService.save(
                Map.of("currency", "CNY", "unknown-key", "x"));

        assertThat(after.get("currency")).isEqualTo("CNY");
        Mockito.verify(platformSettingDao).insert(any(PlatformSettingEntity.class));
        Mockito.verify(platformSettingDao, never()).deleteById("unknown-key");
        Mockito.verify(configAuditService).record(
                Mockito.eq("SETTINGS"), Mockito.eq("global"), Mockito.eq("UPDATE"),
                any(), any());
    }

    @Test
    void saveWithoutEffectiveChangeSkipsAudit() {
        when(platformSettingDao.selectList(any())).thenReturn(List.of());
        when(platformSettingDao.selectById("currency")).thenReturn(null);

        platformSettingService.save(Map.of("currency", "USD"));

        Mockito.verify(configAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void saveNullValueDeletesStoredRowToRestoreDefault() {
        when(platformSettingDao.selectList(any())).thenReturn(List.of(row("currency", "CNY")));

        Map<String, String> values = new java.util.HashMap<>();
        values.put("currency", null);
        platformSettingService.save(values);

        Mockito.verify(platformSettingDao).deleteById("currency");
    }
}
