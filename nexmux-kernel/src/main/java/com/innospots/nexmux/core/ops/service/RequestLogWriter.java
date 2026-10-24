package com.innospots.nexmux.core.ops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * 请求日志写入器（调试台与 Runtime 共用，nexmux-runtime-design §4/§5.2）：
 * 调用方填身份字段（requestId / clientId / routeId / source / routeTrace 等），
 * 本类补齐派生字段后落库：
 * usage tokens（解析响应体）、成本与 pricing 快照（Model pricing_spec × 命中 Binding 倍率）、
 * prompt 快照（按平台设置 promptPolicy）、requested_at 缺省。
 */
public final class RequestLogWriter {

    /** 确定性系统抽样：每四条请求保留一条，保证行为可解释、测试可重复。 */
    private static final AtomicLong PROMPT_SAMPLE_SEQUENCE = new AtomicLong();

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
        write(entity, pricingSpec, bindingCostMultiplier, bindingReferenceMultiplier,
                requestBody, responseBody, null, null, null);
    }

    /** 带 Binding 身份的快照写入，供金额证据链追溯到实际上游。 */
    public void write(RequestLogEntity entity, String pricingSpec,
                      BigDecimal bindingCostMultiplier, BigDecimal bindingReferenceMultiplier,
                      String requestBody, String responseBody,
                      String providerId, String providerModelId, Boolean primary) {
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
        addBindingSnapshot(entity, providerId, providerModelId, primary);
        applyPromptPolicy(entity, requestBody, responseBody);
        requestLogDao.insert(entity);
    }

    private static void addBindingSnapshot(RequestLogEntity entity,
                                           String providerId, String providerModelId, Boolean primary) {
        if (providerId == null || entity.getPricingSnapshot() == null) {
            return;
        }
        Map<String, Object> snapshot = NexmuxJson.read(entity.getPricingSnapshot(), Map.class);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("providerId", providerId);
        binding.put("providerModelId", providerModelId);
        binding.put("primary", primary);
        snapshot.put("binding", binding);
        entity.setPricingSnapshot(NexmuxJson.write(snapshot));
    }

    /** 标准金额先按命中档和时段计算，再分别应用 Binding C/M。 */
    private static void applyPricing(RequestLogEntity entity, JsonNode pricing,
                                     BigDecimal costMultiplier, BigDecimal referenceMultiplier) {
        TierPrice tier = tierOf(pricing, entity.getInputTokens());
        if (tier == null) {
            return;
        }
        TimeMultiplier time = timeMultiplierOf(pricing, entity.getRequestedAt());
        BigDecimal standard = amountOf(entity.getInputTokens(), tier.input())
                .add(amountOf(entity.getOutputTokens(), tier.output()))
                .add(amountOf(entity.getCacheReadTokens(), tier.cacheRead()))
                .add(amountOf(entity.getCacheWriteTokens(), tier.cacheWrite()))
                .multiply(time.rate());
        BigDecimal costRate = costMultiplier == null ? BigDecimal.ONE : costMultiplier;
        BigDecimal billingRate = referenceMultiplier == null ? BigDecimal.ONE : referenceMultiplier;
        entity.setStandardUsd(scale(standard));
        entity.setCostUsd(scale(standard.multiply(costRate)));
        entity.setBillingUsd(scale(standard.multiply(billingRate)));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("currency", textOf(pricing, "currency", "USD"));
        snapshot.put("priceVersion", textOf(pricing, "priceVersion", "unspecified"));
        snapshot.put("priceEffective", textOf(pricing, "priceEffective", null));
        snapshot.put("costMultiplier", costRate);
        snapshot.put("billingMultiplier", billingRate);
        snapshot.put("multiplier", time.rate());
        snapshot.put("multiplierRule", time.rule());
        snapshot.put("tierUpto", tier.upto());
        snapshot.put("tierInputPrice", tier.input());
        snapshot.put("tierOutputPrice", tier.output());
        snapshot.put("tierCacheReadPrice", tier.cacheRead());
        snapshot.put("tierCacheWritePrice", tier.cacheWrite());
        entity.setPricingSnapshot(NexmuxJson.write(snapshot));
    }

    private record TierPrice(Long upto, BigDecimal input, BigDecimal output,
                             BigDecimal cacheRead, BigDecimal cacheWrite) {
        private boolean empty() {
            return input == null && output == null && cacheRead == null && cacheWrite == null;
        }
    }

    private record TimeMultiplier(BigDecimal rate, String rule) {
    }

    /** 平铺价格或分档价格；UPTO 取首个满足档，inputTokensAbove 取最后满足档。 */
    private static TierPrice tierOf(JsonNode pricing, Long inputTokens) {
        if (pricing == null || !pricing.isObject()) {
            return null;
        }
        long tokens = inputTokens == null ? 0 : inputTokens;
        JsonNode tiers = pricing.get("tiers");
        if (tiers != null && tiers.isArray() && !tiers.isEmpty()) {
            boolean uptoFormat = false;
            TierPrice aboveMatch = null;
            for (JsonNode node : tiers) {
                TierPrice candidate = tierOf(node);
                if (node.has("upto") || node.has("upTo")) {
                    uptoFormat = true;
                    if (candidate.upto() == null || tokens <= candidate.upto()) {
                        return candidate.empty() ? null : candidate;
                    }
                } else if (!node.has("inputTokensAbove")
                        || tokens >= node.get("inputTokensAbove").asLong()) {
                    aboveMatch = candidate;
                }
            }
            if (!uptoFormat && aboveMatch != null && !aboveMatch.empty()) {
                return aboveMatch;
            }
            return null;
        }
        TierPrice flat = tierOf(pricing);
        return flat.empty() ? null : flat;
    }

    private static TierPrice tierOf(JsonNode node) {
        Long upto = null;
        JsonNode uptoNode = node.has("upto") ? node.get("upto") : node.get("upTo");
        if (uptoNode != null && uptoNode.isNumber()) {
            upto = uptoNode.longValue();
        }
        return new TierPrice(upto,
                decimalOf(node, "input"), decimalOf(node, "output"),
                decimalOf(node, "cacheRead"), decimalOf(node, "cacheWrite"));
    }

    private static TimeMultiplier timeMultiplierOf(JsonNode pricing, LocalDateTime requestedAt) {
        JsonNode rules = pricing.get("timeMultipliers");
        LocalDateTime time = requestedAt == null ? LocalDateTime.now() : requestedAt;
        if (rules != null && rules.isArray()) {
            for (JsonNode rule : rules) {
                if (!matchesDay(rule.get("days"), time.getDayOfWeek())
                        || !matchesTime(rule, time.toLocalTime())) {
                    continue;
                }
                BigDecimal rate = rule.hasNonNull("rate")
                        ? rule.get("rate").decimalValue()
                        : rule.hasNonNull("multiplier")
                                ? rule.get("multiplier").decimalValue() : BigDecimal.ONE;
                return new TimeMultiplier(rate, rule.toString());
            }
        }
        return new TimeMultiplier(BigDecimal.ONE, "未命中时段倍率（按 1.0 计）");
    }

    private static boolean matchesDay(JsonNode daysNode, DayOfWeek day) {
        String days = daysNode == null || daysNode.isNull() ? "daily" : daysNode.asText();
        String normalized = days.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.equals("daily") || normalized.equals("everyday")
                || normalized.equals("每天") || normalized.equals("*")) {
            return true;
        }
        if (normalized.equals("weekday") || normalized.equals("工作日")) {
            return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        }
        if (normalized.equals("weekend") || normalized.equals("周末")) {
            return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        }
        for (String part : normalized.split("[,，、|]")) {
            if (dayOfWeek(part.trim()) == day) {
                return true;
            }
        }
        return false;
    }

    private static DayOfWeek dayOfWeek(String value) {
        return switch (value) {
            case "monday", "mon", "周一", "星期一", "1" -> DayOfWeek.MONDAY;
            case "tuesday", "tue", "周二", "星期二", "2" -> DayOfWeek.TUESDAY;
            case "wednesday", "wed", "周三", "星期三", "3" -> DayOfWeek.WEDNESDAY;
            case "thursday", "thu", "周四", "星期四", "4" -> DayOfWeek.THURSDAY;
            case "friday", "fri", "周五", "星期五", "5" -> DayOfWeek.FRIDAY;
            case "saturday", "sat", "周六", "星期六", "6" -> DayOfWeek.SATURDAY;
            case "sunday", "sun", "周日", "星期日", "周天", "7" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static boolean matchesTime(JsonNode rule, LocalTime time) {
        try {
            LocalTime start = LocalTime.parse(textOf(rule, "start", "00:00"));
            LocalTime end = LocalTime.parse(textOf(rule, "end", "23:59:59"));
            if (!start.isAfter(end)) {
                return !time.isBefore(start) && !time.isAfter(end);
            }
            return !time.isBefore(start) || !time.isAfter(end);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }

    private static BigDecimal decimalOf(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).isNumber()
                ? node.get(field).decimalValue() : null;
    }

    private static BigDecimal amountOf(Long tokens, BigDecimal price) {
        if (tokens == null || price == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 16, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    /** prompt 快照：full 留存截断原文（4k），其余策略只记策略标记。 */
    private void applyPromptPolicy(RequestLogEntity entity, String requestBody, String responseBody) {
        String policy = platformSettingService == null
                ? "masked"
                : platformSettingService.getAll().getOrDefault("promptPolicy", "masked");
        if ("full".equals(policy) || "sample".equals(policy) && sampledRequest()) {
            entity.setPromptSnapshot(NexmuxJson.write(Map.of(
                    "policy", policy,
                    "prompt", truncate(requestBody),
                    "response", truncate(responseBody))));
        } else {
            entity.setPromptSnapshot(NexmuxJson.write(Map.of(
                    "policy", policy, "policyNote", "按策略未留存")));
        }
    }

    private static boolean sampledRequest() {
        return Math.floorMod(PROMPT_SAMPLE_SEQUENCE.getAndIncrement(), 4) == 3;
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

    private static String truncate(String text) {
        return text == null ? null : text.substring(0, Math.min(4000, text.length()));
    }
}
