package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** G-05：调用日志保留清理（logRetentionDays）。 */
class RequestLogRetentionCleanerTest {

    private RequestLogDao requestLogDao;
    private PlatformSettingService settings;
    private RequestLogRetentionCleaner cleaner;

    @BeforeEach
    void setUp() {
        requestLogDao = Mockito.mock(RequestLogDao.class);
        settings = Mockito.mock(PlatformSettingService.class);
        cleaner = new RequestLogRetentionCleaner(requestLogDao, settings);
    }

    @Test
    void cleanDeletesRowsOlderThanRetentionDays() {
        when(settings.getAll()).thenReturn(Map.of("logRetentionDays", "90"));
        when(requestLogDao.delete(any())).thenReturn(5);

        int deleted = cleaner.clean();

        assertThat(deleted).isEqualTo(5);
        Mockito.verify(requestLogDao).delete(any());
    }

    @Test
    void cleanSkipsWhenRetentionDisabled() {
        // 0 / 负值 = 不清理（保守：不误删）
        when(settings.getAll()).thenReturn(Map.of("logRetentionDays", "0"));

        assertThat(cleaner.clean()).isZero();
        Mockito.verifyNoInteractions(requestLogDao);
    }

    @Test
    void cleanSkipsOnInvalidValue() {
        when(settings.getAll()).thenReturn(Map.of("logRetentionDays", "abc"));

        assertThat(cleaner.clean()).isZero();
        Mockito.verifyNoInteractions(requestLogDao);
    }

    @Test
    void cleanSkipsWhenKeyMissing() {
        when(settings.getAll()).thenReturn(Map.of());

        assertThat(cleaner.clean()).isZero();
        Mockito.verifyNoInteractions(requestLogDao);
    }
}
