package com.innospots.nexmux.core.ops.service;

import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogWriterTest {

    private RequestLogDao requestLogDao;
    private PlatformSettingService platformSettingService;
    private RequestLogWriter writer;

    @BeforeEach
    void setUp() {
        requestLogDao = Mockito.mock(RequestLogDao.class);
        platformSettingService = Mockito.mock(PlatformSettingService.class);
        when(platformSettingService.getAll()).thenReturn(PlatformSettingService.DEFAULTS);
        writer = new RequestLogWriter(requestLogDao, platformSettingService);
    }

    private RequestLogEntity entity() {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId("rq-1");
        entity.setModelCode("openai/o4-mini");
        entity.setStatus("SUCCESS");
        return entity;
    }

    private static String responseWithUsage(long in, long out) {
        return "{\"choices\":[],\"usage\":{\"prompt_tokens\":" + in
                + ",\"completion_tokens\":" + out + "}}";
    }

    @Test
    void fillsUsageTokensFromResponseBody() {
        writer.write(entity(), null, null, null, "{}", responseWithUsage(11, 7));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                Long.valueOf(11).equals(log.getInputTokens())
                        && Long.valueOf(7).equals(log.getOutputTokens())
                        && log.getRequestedAt() != null));
    }

    @Test
    void computesCostFromPricingSpec() {
        // input $10/M、output $30/M；usage 1000 in + 500 out → 0.01 + 0.015 = 0.025
        String pricing = "{\"currency\":\"USD\",\"input\":10,\"output\":30}";

        writer.write(entity(), pricing, null, null, "{}", responseWithUsage(1000, 500));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                new BigDecimal("0.02500000").compareTo(log.getCostUsd()) == 0
                        && log.getPricingSnapshot() != null
                        && log.getPricingSnapshot().contains("\"currency\":\"USD\"")));
    }

    @Test
    void skipsCostWhenNoPricingSpec() {
        writer.write(entity(), null, null, null, "{}", responseWithUsage(1000, 500));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getCostUsd() == null && log.getPricingSnapshot() == null));
    }

    @Test
    void maskedPolicyDoesNotPersistPromptContent() {
        writer.write(entity(), null, null, null, "{\"prompt\":\"secret\"}", responseWithUsage(1, 1));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getPromptSnapshot() != null
                        && log.getPromptSnapshot().contains("\"policy\":\"masked\"")
                        && !log.getPromptSnapshot().contains("secret")));
    }

    @Test
    void fullPolicyPersistsPromptAndResponse() {
        Map<String, String> settings = new LinkedHashMap<>(PlatformSettingService.DEFAULTS);
        settings.put("promptPolicy", "full");
        when(platformSettingService.getAll()).thenReturn(settings);

        writer.write(entity(), null, null, null, "{\"prompt\":\"hello\"}", responseWithUsage(1, 1));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getPromptSnapshot() != null
                        && log.getPromptSnapshot().contains("hello")
                        && !log.getPromptSnapshot().contains("chat.completion")));
    }

    @Test
    void toleratesNonJsonResponseBody() {
        writer.write(entity(), null, null, null, "{}", "not-json");

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>any());
    }
}
