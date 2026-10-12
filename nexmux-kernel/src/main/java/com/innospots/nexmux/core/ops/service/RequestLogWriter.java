package com.innospots.nexmux.core.ops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求日志写入器（调试台与 Runtime 共用，nexmux-runtime-design §4/§5.2）：
 * 调用方填身份字段（requestId / clientId / routeId / source / routeTrace 等），
 * 本类补齐派生字段后落库：
 * usage tokens（解析响应体）、成本与 pricing 快照（Model pricing_spec × 命中 Binding 倍率）、
 * prompt 快照（按平台设置 promptPolicy）、requested_at 缺省。
 */
public final class RequestLogWriter {

    private final RequestLogDao requestLogDao;
    private final PlatformSettingService platformSettingService;

    public RequestLogWriter(RequestLogDao requestLogDao, PlatformSettingService platformSettingService) {
        this.requestLogDao = requestLogDao;
        this.platformSettingService = platformSettingService;
    }

    /**
     * 补齐派生字段并插入 {@code mux_request_log}。
     *
     * @param entity         已填身份字段的实体（{@code requestedAt} 为空时补当前时间）
     * @param pricingSpec    Model 定价 JSON（可空；无定价则不写成本）
     * @param bindingCostMultiplier     命中 Binding 成本倍率 C（可空 = 1）
     * @param bindingReferenceMultiplier 命中 Binding 参考倍率 M（可空 = 1）
     * @param requestBody    请求体原文（promptPolicy=full 时留存）
     * @param responseBody   响应体原文（解析 usage；非 JSON 容错跳过）
     */
    public void write(RequestLogEntity entity, String pricingSpec,
                      BigDecimal bindingCostMultiplier, BigDecimal bindingReferenceMultiplier,
                      String requestBody, String responseBody) {
        if (entity.getRequestedAt() == null) {
            entity.setRequestedAt(LocalDateTime.now());
        }

        JsonNode body = readJsonTolerant(responseBody);
        JsonNode usage = body == null ? null : body.get("usage");
        entity.setInputTokens(longOrNull(usage, "prompt_tokens"));
        entity.setOutputTokens(longOrNull(usage, "completion_tokens"));
        entity.setCacheReadTokens(longOrNull(usage, "prompt_cache_hit_tokens"));
        entity.setCacheWriteTokens(longOrNull(usage, "prompt_cache_miss_tokens"));

        applyPricing(entity, readJsonTolerant(pricingSpec),
                bindingCostMultiplier, bindingReferenceMultiplier);
        applyPromptPolicy(entity, requestBody, responseBody);
        requestLogDao.insert(entity);
    }

    /** 成本 = tokens × 单价 × 倍率；pricing_snapshot 记录当时定价与倍率。 */
    private static void applyPricing(RequestLogEntity entity, JsonNode pricing,
                                     BigDecimal costMultiplier, BigDecimal referenceMultiplier) {
        BigDecimal inputPrice = priceOf(pricing, "input");
        BigDecimal outputPrice = priceOf(pricing, "output");
        if (inputPrice == null && outputPrice == null) {
            return;
        }
        BigDecimal costRate = costMultiplier == null ? BigDecimal.ONE : costMultiplier;
        BigDecimal billingRate = referenceMultiplier == null ? BigDecimal.ONE : referenceMultiplier;
        BigDecimal cost = costOf(entity.getInputTokens(), inputPrice).multiply(costRate)
                .add(costOf(entity.getOutputTokens(), outputPrice).multiply(costRate));
        BigDecimal billing = costOf(entity.getInputTokens(), inputPrice).multiply(billingRate)
                .add(costOf(entity.getOutputTokens(), outputPrice).multiply(billingRate));
        entity.setCostUsd(cost);
        entity.setBillingUsd(billing);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("currency", pricing.hasNonNull("currency") ? pricing.get("currency").asText() : "USD");
        snapshot.put("priceVersion", pricing.hasNonNull("priceVersion")
                ? pricing.get("priceVersion").asText() : "unspecified");
        snapshot.put("costMultiplier", costRate);
        // 兼容键：Console 成本/日志视图读 multiplier 作「时段倍率」展示
        snapshot.put("multiplier", costRate);
        snapshot.put("billingMultiplier", billingRate);
        snapshot.put("tierInputPrice", inputPrice == null ? 0 : inputPrice);
        snapshot.put("tierOutputPrice", outputPrice == null ? 0 : outputPrice);
        entity.setPricingSnapshot(NexmuxJson.write(snapshot));
    }

    /** prompt 快照：full 留存截断原文（4k），其余策略只记策略标记。 */
    private void applyPromptPolicy(RequestLogEntity entity, String requestBody, String responseBody) {
        String policy = platformSettingService == null
                ? "masked"
                : platformSettingService.getAll().getOrDefault("promptPolicy", "masked");
        if ("full".equals(policy)) {
            entity.setPromptSnapshot(NexmuxJson.write(Map.of(
                    "policy", "full",
                    "prompt", truncate(requestBody),
                    "response", truncate(responseBody))));
        } else {
            entity.setPromptSnapshot(NexmuxJson.write(Map.of(
                    "policy", policy, "policyNote", "按策略未留存")));
        }
    }

    private static JsonNode readJsonTolerant(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isNumber()
                ? node.get(field).longValue() : null;
    }

    /** 单价：平铺 input/output 或首档 tiers[0]。 */
    private static BigDecimal priceOf(JsonNode pricing, String key) {
        if (pricing == null) {
            return null;
        }
        if (pricing.hasNonNull(key) && pricing.get(key).isNumber()) {
            return pricing.get(key).decimalValue();
        }
        JsonNode tiers = pricing.get("tiers");
        if (tiers != null && tiers.isArray() && !tiers.isEmpty()
                && tiers.get(0).hasNonNull(key) && tiers.get(0).get(key).isNumber()) {
            return tiers.get(0).get(key).decimalValue();
        }
        return null;
    }

    private static BigDecimal costOf(Long tokens, BigDecimal price) {
        if (tokens == null || price == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }

    private static String truncate(String text) {
        return text == null ? null : text.substring(0, Math.min(4000, text.length()));
    }
}
