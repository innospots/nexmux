package com.innospots.nexmux.core.ops.service;

import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogDetailVo;
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

class RequestLogQueryServiceTest {

    private RequestLogDao requestLogDao;
    private RequestLogQueryService service;

    @BeforeEach
    void setUp() {
        requestLogDao = Mockito.mock(RequestLogDao.class);
        service = new RequestLogQueryService(requestLogDao);
    }

    private RequestLogEntity log(String id, String status) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(id);
        entity.setClientId("cl-1");
        entity.setRouteId("rt-1");
        entity.setModelCode("openai/o4-mini");
        entity.setStatus(status);
        entity.setLatencyMs(320L);
        entity.setRequestedAt(LocalDateTime.of(2026, 9, 18, 14, 32, 18));
        entity.setAppKeyId("gw-ak-7f3a2b1c");
        entity.setUserEmail("dev@example.com");
        entity.setProviderCode("openai");
        entity.setProtocol("OpenAI / Chat Completions");
        entity.setRouteGroup("默认分组");
        entity.setApplication("mobile-app");
        entity.setAlias("general");
        entity.setHttpStatus(200);
        entity.setInputTokens(2100L);
        entity.setOutputTokens(1800L);
        entity.setCacheReadTokens(0L);
        entity.setCacheWriteTokens(0L);
        entity.setCostUsd(new BigDecimal("0.0106"));
        entity.setBillingUsd(new BigDecimal("0.0127"));
        entity.setTtftMs(180L);
        entity.setRetries(0);
        entity.setTraceId("trace-1c4dreq-9f2a");
        return entity;
    }

    @Test
    void getReturnsDetailWithSnapshots() {
        RequestLogEntity entity = log("req-9f2a1c4d", "SUCCESS");
        entity.setRouteTrace("[{\"name\":\"match\"}]");
        entity.setPricingSnapshot("{\"priceVersion\":\"pricing@v7\"}");
        entity.setPromptSnapshot("{\"policy\":\"masked\"}");
        when(requestLogDao.selectById("req-9f2a1c4d")).thenReturn(entity);

        RequestLogDetailVo detail = service.get("req-9f2a1c4d");

        assertThat(detail.requestId()).isEqualTo("req-9f2a1c4d");
        assertThat(detail.appKeyId()).isEqualTo("gw-ak-7f3a2b1c");
        assertThat(detail.inputTokens()).isEqualTo(2100L);
        assertThat(detail.routeTrace()).contains("match");
        assertThat(detail.pricingSnapshot()).contains("pricing@v7");
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(requestLogDao.selectById("req-x")).thenReturn(null);

        assertThatThrownBy(() -> service.get("req-x"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(
                                NexmuxStatusCode.RESOURCE_NOT_FOUND.fullCode()));
    }

    @Test
    void pageAppliesTimeRangeAndDimensionFilters() {
        when(requestLogDao.selectCount(any())).thenReturn(1L);
        when(requestLogDao.selectList(any())).thenReturn(List.of(log("req-1", "SUCCESS")));

        var result = service.page(new RequestLogPageRequest(
                1L, 20L, "2026-09-18", "2026-09-18", "SUCCESS",
                "openai/o4-mini", "openai", "OpenAI / Chat Completions", "默认分组",
                "dev@", "gw-ak"));

        assertThat(result.records()).hasSize(1);
        Mockito.verify(requestLogDao).selectList(any());
    }
}
