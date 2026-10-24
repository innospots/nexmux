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
    void computesStandardCostAndBillingIndependently() {
        String pricing = "{\"currency\":\"USD\",\"input\":10,\"output\":20}";

        writer.write(entity(), pricing, BigDecimal.valueOf(2), BigDecimal.valueOf(3),
                "{}", responseWithUsage(12, 6));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                new BigDecimal("0.00024000").compareTo(log.getStandardUsd()) == 0
                        && new BigDecimal("0.00048000").compareTo(log.getCostUsd()) == 0
                        && new BigDecimal("0.00072000").compareTo(log.getBillingUsd()) == 0));
    }

    @Test
    void computesCostFromMatchingTier() {
        String pricing = "{\"currency\":\"USD\",\"tiers\":[{\"upto\":10,\"input\":1,\"output\":2},"
                + "{\"upto\":1000,\"input\":10,\"output\":20}]}";

        writer.write(entity(), pricing, null, null, "{}", responseWithUsage(12, 6));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                new BigDecimal("0.00024000").compareTo(log.getCostUsd()) == 0));
    }

    @Test
    void includesCacheReadAndWritePrices() {
        String pricing = "{\"currency\":\"USD\",\"input\":10,\"output\":20,"
                + "\"cacheRead\":1,\"cacheWrite\":2}";
        String response = "{\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":6,"
                + "\"prompt_cache_hit_tokens\":4,\"prompt_cache_miss_tokens\":8}}";

        writer.write(entity(), pricing, null, null, "{}", response);

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                new BigDecimal("0.00026000").compareTo(log.getCostUsd()) == 0));
    }

    @Test
    void appliesMatchingTimeMultiplierToStandardAmount() {
        String pricing = "{\"currency\":\"USD\",\"input\":10,\"output\":20,"
                + "\"timeMultipliers\":[{\"days\":\"daily\",\"start\":\"00:00\","
                + "\"end\":\"23:59\",\"rate\":2}]}";

        writer.write(entity(), pricing, null, null, "{}", responseWithUsage(12, 6));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                new BigDecimal("0.00048000").compareTo(log.getCostUsd()) == 0
                        && new BigDecimal("0.00048000").compareTo(log.getBillingUsd()) == 0));
    }

    @Test
    void snapshotContainsEffectiveTierAndMultiplierEvidence() {
        String pricing = "{\"currency\":\"USD\",\"priceVersion\":\"v2\","
                + "\"priceEffective\":\"2026-10-24T00:00:00\",\"input\":10,\"output\":20,"
                + "\"cacheRead\":1,\"cacheWrite\":2,"
                + "\"timeMultipliers\":[{\"days\":\"daily\",\"start\":\"00:00\","
                + "\"end\":\"23:59\",\"rate\":2}]}";

        writer.write(entity(), pricing, null, null, "{}", responseWithUsage(12, 6));

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getPricingSnapshot().contains("\"priceEffective\":\"2026-10-24T00:00:00\"")
                        && log.getPricingSnapshot().contains("\"tierCacheReadPrice\":1")
                        && log.getPricingSnapshot().contains("\"tierCacheWritePrice\":2")
                        && log.getPricingSnapshot().contains("\"multiplier\":2")
                        && log.getPricingSnapshot().contains("multiplierRule")));
    }

    @Test
    void snapshotContainsBindingIdentity() {
        String pricing = "{\"currency\":\"USD\",\"input\":10,\"output\":20}";

        writer.write(entity(), pricing, null, null, "{}", responseWithUsage(12, 6),
                "pv-1", "stub-a", true);

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>argThat(log ->
                log.getPricingSnapshot().contains("\"providerId\":\"pv-1\"")
                        && log.getPricingSnapshot().contains("\"providerModelId\":\"stub-a\"")
                        && log.getPricingSnapshot().contains("\"primary\":true")));
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
    void samplePolicyUsesDeterministicSampling() {
        Map<String, String> settings = new LinkedHashMap<>(PlatformSettingService.DEFAULTS);
        settings.put("promptPolicy", "sample");
        when(platformSettingService.getAll()).thenReturn(settings);

        for (int i = 0; i < 4; i++) {
            RequestLogEntity entity = entity();
            entity.setRequestId("rq-sample-" + i);
            writer.write(entity, null, null, null, "{\"prompt\":\"hello\"}", responseWithUsage(1, 1));
        }

        verify(requestLogDao, Mockito.times(4)).insert(Mockito.<RequestLogEntity>argThat(log ->
                (log.getRequestId().equals("rq-sample-3") && log.getPromptSnapshot().contains("hello"))
                        || (!log.getRequestId().equals("rq-sample-3")
                                && !log.getPromptSnapshot().contains("hello"))));
    }

    @Test
    void toleratesNonJsonResponseBody() {
        writer.write(entity(), null, null, null, "{}", "not-json");

        verify(requestLogDao).insert(Mockito.<RequestLogEntity>any());
    }
}
