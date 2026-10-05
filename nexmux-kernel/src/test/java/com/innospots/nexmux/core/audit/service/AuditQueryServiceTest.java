package com.innospots.nexmux.core.audit.service;

import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogDetailVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {

    private ConfigAuditLogDao auditLogDao;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        auditLogDao = Mockito.mock(ConfigAuditLogDao.class);
        service = new AuditQueryService(auditLogDao);
    }

    private ConfigAuditLogEntity audit(String id) {
        ConfigAuditLogEntity entity = new ConfigAuditLogEntity();
        entity.setAuditId(id);
        entity.setResourceType("ROUTE");
        entity.setResourceId("rt-1");
        entity.setOperation("PUBLISH");
        entity.setChangeSummary("首次发布");
        entity.setChangeDiff("{\"before\":null,\"after\":{\"alias\":\"general\"}}");
        entity.setOperatorId("zhangsan");
        entity.setClientIp("10.0.0.8");
        entity.setRequestId("req-audit-1");
        entity.setUserAgent("nexmux-console/1.0");
        entity.setCreatedAt(LocalDateTime.of(2026, 9, 18, 14, 30, 0));
        return entity;
    }

    @Test
    void getReturnsDetailWithContext() {
        when(auditLogDao.selectById("au-1")).thenReturn(audit("au-1"));

        AuditLogDetailVo detail = service.get("au-1");

        assertThat(detail.auditId()).isEqualTo("au-1");
        assertThat(detail.changeDiff()).contains("after");
        assertThat(detail.clientIp()).isEqualTo("10.0.0.8");
        assertThat(detail.requestId()).isEqualTo("req-audit-1");
        assertThat(detail.userAgent()).isEqualTo("nexmux-console/1.0");
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(auditLogDao.selectById("au-x")).thenReturn(null);

        assertThatThrownBy(() -> service.get("au-x"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    @Test
    void pageAppliesFilters() {
        when(auditLogDao.selectCount(any())).thenReturn(1L);
        when(auditLogDao.selectList(any())).thenReturn(List.of(audit("au-1")));

        var result = service.page(new AuditLogPageRequest(
                1L, 20L, "2026-09-18", "2026-09-18", "zhangsan", "PUBLISH", "ROUTE", "rt-1"));

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).clientIp()).isEqualTo("10.0.0.8");
    }

    @Test
    void operatorsReturnsDistinctOperatorIds() {
        when(auditLogDao.selectList(any())).thenReturn(List.of(audit("au-1"), audit("au-2")));

        var operators = service.operators();

        assertThat(operators).containsExactly("zhangsan");
    }
}
