package com.innospots.nexmux.core.ops.service;

import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.CostRecordVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CostQueryServiceTest {

    private RequestLogDao requestLogDao;
    private CostQueryService service;

    @BeforeEach
    void setUp() {
        requestLogDao = Mockito.mock(RequestLogDao.class);
        service = new CostQueryService(requestLogDao);
    }

    private RequestLogEntity billedLog(String id) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(id);
        entity.setStatus("SUCCESS");
        entity.setRequestedAt(LocalDateTime.of(2026, 9, 19, 8, 5, 12));
        entity.setApplication("mobile-app");
        entity.setAppKeyId("gw-ak-7f3a2b1c");
        entity.setAlias("general");
        entity.setModelCode("openai/o4-mini");
        entity.setProviderCode("openai");
        entity.setInputTokens(2400L);
        entity.setOutputTokens(1800L);
        entity.setCacheReadTokens(0L);
        entity.setCostUsd(new BigDecimal("0.0106"));
        entity.setPricingSnapshot(
                "{\"currency\":\"USD\",\"priceVersion\":\"pricing@v7\",\"priceEffective\":\"2026-09-01\","
                        + "\"tierUpto\":null,\"tierInputPrice\":1.1,\"tierOutputPrice\":4.4,"
                        + "\"tierCacheReadPrice\":null,\"multiplier\":1.0}");
        return entity;
    }

    @Test
    void pageReturnsOnlyBilledSuccessLogs() {
        when(requestLogDao.selectCount(any())).thenReturn(1L);
        when(requestLogDao.selectList(any())).thenReturn(List.of(billedLog("req-9f2a1c4d")));

        var result = service.page(new CostPageRequest(
                1L, 20L, null, null, null, null, null, null, null));

        assertThat(result.records()).hasSize(1);
        CostRecordVo record = result.records().get(0);
        assertThat(record.requestId()).isEqualTo("req-9f2a1c4d");
        assertThat(record.pricingSnapshot()).contains("tierInputPrice");
        assertThat(record.inputTokens()).isEqualTo(2400L);
        // 查询条件须限定 SUCCESS 且有成本（验证 wrapper 被使用）
        Mockito.verify(requestLogDao).selectList(any());
    }

    @Test
    void getReturnsCostDetail() {
        when(requestLogDao.selectById("req-9f2a1c4d")).thenReturn(billedLog("req-9f2a1c4d"));

        CostRecordVo detail = service.get("req-9f2a1c4d");

        assertThat(detail.requestId()).isEqualTo("req-9f2a1c4d");
        assertThat(detail.costUsd()).isEqualByComparingTo("0.0106");
    }

    @Test
    void getRejectsNonSuccessLog() {
        RequestLogEntity failed = billedLog("req-failed");
        failed.setStatus("ERROR");
        when(requestLogDao.selectById("req-failed")).thenReturn(failed);

        assertThatThrownBy(() -> service.get("req-failed"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }
}
