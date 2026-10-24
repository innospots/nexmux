package com.innospots.nexmux.console.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * 配额 / 限流规则的展示派生（与原 BFF quota-shared 口径逐字对齐）：
 * *_spec CLOB JSON → 含展示标签的规则记录、摘要文案。Client 与 ClientTemplate
 * 两个装配器共用。
 */
public final class PolicyDerive {

    private static final Map<String, String> QUOTA_TYPE_LABELS = Map.of(
            "lifetime", "总配额",
            "cyclic", "周期循环配额");

    private static final Map<String, String> QUOTA_METRIC_LABELS = Map.of(
            "REQUEST", "Request",
            "INPUT_TOKEN", "Input Token",
            "OUTPUT_TOKEN", "Output Token",
            "TOTAL_TOKEN", "Total Token",
            "COST", "Cost",
            "CONCURRENT", "Concurrent");

    private static final Map<String, String> QUOTA_MODE_LABELS = Map.of(
            "soft", "Soft Limit",
            "hard", "Hard Limit");

    private static final Map<String, String> RATE_LIMIT_TYPE_LABELS = Map.of(
            "RPM", "RPM（每分钟请求数）",
            "TPM", "TPM（每分钟 Token 数）",
            "CONCURRENT", "Concurrent（并发连接数）");

    private PolicyDerive() {
    }

    /** 千分位（en-US）。 */
    public static String thousands(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    /** quota_spec CLOB → 含展示标签的配额规则（非法 JSON 容错为空数组）。 */
    public static List<QuotaRule> quotasOf(String quotaSpec) {
        List<QuotaRule> out = new ArrayList<>();
        for (JsonNode rule : parseSpec(quotaSpec)) {
            String quotaType = text(rule, "quotaType");
            String cycleUnit = textOrNull(rule, "cycleUnit");
            Integer cycleValue = rule.has("cycleValue") && rule.get("cycleValue").isNumber()
                    ? rule.get("cycleValue").asInt() : null;
            long limit = rule.has("limit") ? rule.get("limit").asLong(0) : 0;
            out.add(new QuotaRule(
                    text(rule, "name"),
                    quotaType,
                    QUOTA_TYPE_LABELS.getOrDefault(quotaType, quotaType),
                    text(rule, "metric"),
                    QUOTA_METRIC_LABELS.getOrDefault(text(rule, "metric"), text(rule, "metric")),
                    limit,
                    thousands(limit),
                    text(rule, "mode"),
                    QUOTA_MODE_LABELS.getOrDefault(text(rule, "mode"), text(rule, "mode")),
                    cycleUnit,
                    cycleValue,
                    "cyclic".equals(quotaType) && cycleUnit != null
                            ? cycleLabel(cycleUnit, cycleValue) : null,
                    text(rule, "status"),
                    null, null, null, null));
        }
        return out;
    }

    /** rate_limit_spec CLOB → 含展示标签的限流规则（scopeLabel 路由名由 routeNameOf 补）。 */
    public static List<RateLimitRule> rateLimitsOf(String rateLimitSpec, Function<String, String> routeNameOf) {
        List<RateLimitRule> out = new ArrayList<>();
        for (JsonNode rule : parseSpec(rateLimitSpec)) {
            String limitType = text(rule, "limitType");
            long limit = rule.has("limit") ? rule.get("limit").asLong(0) : 0;
            boolean scopeAll = rule.has("scopeAll") && rule.get("scopeAll").asBoolean(false);
            List<String> scopeRoutes = new ArrayList<>();
            JsonNode routes = rule.get("scopeRoutes");
            if (routes != null && routes.isArray()) {
                routes.forEach(r -> scopeRoutes.add(r.asText()));
            }
            String scopeLabel;
            if (scopeAll) {
                scopeLabel = "全部授权路由";
            } else {
                List<String> names = scopeRoutes.stream().map(routeNameOf)
                        .filter(n -> n != null && !n.isEmpty()).toList();
                scopeLabel = names.isEmpty() ? "—" : String.join("、", names);
            }
            out.add(new RateLimitRule(
                    text(rule, "name"),
                    limitType,
                    RATE_LIMIT_TYPE_LABELS.getOrDefault(limitType, limitType),
                    limit,
                    thousands(limit),
                    rule.has("burst") && rule.get("burst").isNumber() ? rule.get("burst").asLong() : null,
                    scopeAll,
                    scopeRoutes,
                    scopeLabel,
                    text(rule, "status")));
        }
        return out;
    }

    /** 配额摘要：总+日循环 / 月循环 …；无规则为 —。 */
    public static String quotaSummaryOf(List<QuotaRule> quotas) {
        List<String> parts = quotas.stream().map(q -> {
            if ("lifetime".equals(q.quotaType())) {
                return "总";
            }
            if ("month".equals(q.cycleUnit())) {
                return "月循环";
            }
            if ("day".equals(q.cycleUnit())) {
                return Integer.valueOf(7).equals(q.cycleValue()) ? "周循环" : "日循环";
            }
            return "日循环";
        }).toList();
        return parts.isEmpty() ? "—" : String.join("+", parts);
    }

    /** 限流摘要：首个启用规则（否则首条）的紧凑文案；无规则为 —。 */
    public static String rateLimitSummaryOf(List<RateLimitRule> rateLimits) {
        RateLimitRule rule = rateLimits.stream()
                .filter(r -> "enabled".equals(r.status()))
                .findFirst()
                .orElse(rateLimits.isEmpty() ? null : rateLimits.get(0));
        if (rule == null) {
            return "—";
        }
        if ("CONCURRENT".equals(rule.limitType())) {
            return rule.limit() + " 并发";
        }
        return rule.limit() + " " + rule.limitType();
    }

    private static String cycleLabel(String unit, Integer value) {
        if ("month".equals(unit)) {
            return "每月（自然月）";
        }
        if ("day".equals(unit)) {
            return "每 " + (value == null ? 1 : value) + " 天";
        }
        return "每 " + (value == null ? 24 : value) + " 小时";
    }

    /** *_spec CLOB JSON 数组容错解析（空 / 非法 / 非数组 → 空列表）。 */
    static List<JsonNode> parseSpec(String text) {
        JsonNode node;
        try {
            node = NexmuxJson.read(text, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    /** JSON 字符串数组容错解析（tags 等）。 */
    public static List<String> stringListOf(String json) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : parseSpec(json)) {
            if (item.isTextual()) {
                out.add(item.asText());
            }
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
