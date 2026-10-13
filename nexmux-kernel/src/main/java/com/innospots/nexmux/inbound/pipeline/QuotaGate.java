package com.innospots.nexmux.inbound.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 配额关口（PRD §28.2，nexmux-runtime-design §9）：消费 Client {@code quota_spec}
 * （lifetime / cyclic × REQUEST/INPUT_TOKEN/OUTPUT_TOKEN/TOTAL_TOKEN/COST × soft/hard）。
 * 进程内计数器 + 窗口推算（cyclic 按 cycleUnit/cycleValue 起算，month 为自然月）；
 * hard 超限抛 429 {@code QUOTA_EXCEEDED}，soft 仅放行。
 *
 * <p>G-07：配额不再接受 CONCURRENT 指标（配额为「窗口内累计」语义，并发控制归
 * {@link RateLimitGate} 的 CONCURRENT 信号量）；存量 CONCURRENT 配额规则直接忽略。
 *
 * <p>计数为进程内实例级语义（多实例部署非全局精确，ADR-0002 后果已声明）；
 * 进程重启后计数清零、由 {@link #resync} 以运行库聚合回填（RuntimeSnapshotRefresher 周期触发）。
 */
public final class QuotaGate {

    /** 配额规则（quota_spec JSON 元素的消费视图）。 */
    record QuotaRuleSpec(String name, String quotaType, String metric, long limit, String mode,
                         String cycleUnit, Integer cycleValue) {
    }

    /** 计数桶：规则 + 窗口起点。 */
    private record BucketKey(String clientId, String ruleKey, LocalDateTime windowStart) {
    }

    private final Map<BucketKey, LongAdder> counters = new ConcurrentHashMap<>();

    /**
     * 请求前检查：任一 enabled hard 规则超限 → 429。
     *
     * @return 触发的 soft 规则名列表（routeTrace 用；无 soft 触发为空表）
     */
    public List<String> check(ClientEntity client) {
        List<String> softTriggered = new ArrayList<>();
        for (QuotaRuleSpec rule : enabledRules(client)) {
            long used = currentUsage(client.getClientId(), rule);
            // COST 计数为 1e-8 美元刻度，limit 换算到同一刻度
            long effectiveLimit = "COST".equals(rule.metric())
                    ? rule.limit() * 100_000_000L : rule.limit();
            if (used < effectiveLimit) {
                continue;
            }
            if ("hard".equals(rule.mode())) {
                throw NexusException.build(NexmuxStatusCode.QUOTA_EXCEEDED,
                        "quota exceeded: " + rule.name() + " (" + rule.metric() + " " + used + "/"
                                + rule.limit() + ")");
            }
            softTriggered.add(rule.name());
        }
        return softTriggered;
    }

    /** 调用完成后记账（tokens / 成本回填；请求数在 {@link #recordRequest} 单独计）。 */
    public void recordUsage(ClientEntity client, long inputTokens, long outputTokens, BigDecimal costUsd) {
        for (QuotaRuleSpec rule : enabledRules(client)) {
            long amount = switch (rule.metric() == null ? "" : rule.metric()) {
                case "INPUT_TOKEN" -> inputTokens;
                case "OUTPUT_TOKEN" -> outputTokens;
                case "TOTAL_TOKEN" -> inputTokens + outputTokens;
                case "COST" -> costUsd == null ? 0 : costUsd.movePointRight(8).longValue();
                default -> 0;
            };
            if (amount > 0) {
                bucket(client.getClientId(), rule).add(amount);
            }
        }
    }

    /** 请求数记账（检查通过后计；拒绝的请求不消耗配额）。 */
    public void recordRequest(ClientEntity client) {
        for (QuotaRuleSpec rule : enabledRules(client)) {
            if ("REQUEST".equals(rule.metric())) {
                bucket(client.getClientId(), rule).add(1);
            }
        }
    }

    /**
     * 以运行库聚合回填计数（进程重启 / 周期校准）。{@code usageByMetric} 键为
     * metric 名，值为该客户端当前生效窗口内的累计量（由调用方按窗口查询）。
     */
    public void resync(String clientId, List<QuotaRuleSpec> rules, Map<String, Long> usageByMetric) {
        for (QuotaRuleSpec rule : rules) {
            Long dbValue = usageByMetric.get(rule.metric());
            if (dbValue == null) {
                continue;
            }
            BucketKey key = bucketKey(clientId, rule);
            LongAdder adder = counters.get(key);
            long local = adder == null ? 0 : adder.sum();
            long delta = dbValue - local;
            if (delta > 0) {
                bucket(clientId, rule).add(delta);
            }
        }
    }

    /** 客户端启用规则（quota_spec 为空 / 非法 → 不限量）。 */
    static List<QuotaRuleSpec> enabledRules(ClientEntity client) {
        String spec = client.getQuotaSpec();
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        List<QuotaRuleSpec> rules = new ArrayList<>();
        try {
            for (JsonNode node : NexmuxJson.read(spec, JsonNode.class)) {
                if (node.has("status") && !"enabled".equals(node.get("status").asText())) {
                    continue;
                }
                // G-07：CONCURRENT 已从配额指标移除（并发归限流侧），存量规则忽略
                if (node.has("metric") && "CONCURRENT".equals(node.get("metric").asText())) {
                    continue;
                }
                rules.add(new QuotaRuleSpec(
                        text(node, "name"), text(node, "quotaType"), text(node, "metric"),
                        node.has("limit") ? node.get("limit").asLong(0) : 0,
                        text(node, "mode"), text(node, "cycleUnit"),
                        node.has("cycleValue") && node.get("cycleValue").isNumber()
                                ? node.get("cycleValue").asInt() : null));
            }
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return rules;
    }

    /** 当前用量（窗口过期自动归零）。 */
    private long currentUsage(String clientId, QuotaRuleSpec rule) {
        LongAdder adder = counters.get(bucketKey(clientId, rule));
        return adder == null ? 0 : adder.sum();
    }

    private LongAdder bucket(String clientId, QuotaRuleSpec rule) {
        return counters.computeIfAbsent(bucketKey(clientId, rule), k -> new LongAdder());
    }

    /** 桶键含窗口起点：cyclic 窗口滚动后自然落到新桶（旧桶随内存常驻，P0 规模可接受）。 */
    private static BucketKey bucketKey(String clientId, QuotaRuleSpec rule) {
        return new BucketKey(clientId, ruleKey(rule), windowStart(rule));
    }

    private static String ruleKey(QuotaRuleSpec rule) {
        return rule.name() + "|" + rule.metric() + "|" + rule.quotaType();
    }

    /** 窗口起点：lifetime → 纪元；cyclic → 按周期对齐（hour/day 截断，month 自然月）。 */
    static LocalDateTime windowStart(QuotaRuleSpec rule) {
        if (!"cyclic".equals(rule.quotaType())) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int value = rule.cycleValue() == null || rule.cycleValue() <= 0 ? 1 : rule.cycleValue();
        return switch (rule.cycleUnit() == null ? "day" : rule.cycleUnit()) {
            case "hour" -> now.truncatedTo(ChronoUnit.HOURS);
            case "month" -> now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            default -> now.truncatedTo(ChronoUnit.DAYS);
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
