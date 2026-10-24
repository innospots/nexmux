package com.innospots.nexmux.inbound.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/**
 * 限流关口（PRD §28.3，nexmux-runtime-design §9）：消费 Client {@code rate_limit_spec}
 * （RPM / TPM / CONCURRENT × scopeAll|scopeRoutes）。进程内实现：RPM/TPM 为分钟级固定窗口
 * 计数（burst 作为窗口冗余量），CONCURRENT 为信号量。实例级语义（多实例非全局精确）。
 */
public final class RateLimitGate {

    /** 限流规则（rate_limit_spec JSON 元素的消费视图）。 */
    record RateRuleSpec(String name, String limitType, long limit, long burst, boolean scopeAll,
                        List<String> scopeRoutes) {
    }

    /** 分钟窗口计数桶。 */
    private record WindowCounter(long minuteEpoch, LongAdder count) {
    }

    /** 并发许可桶：规则限额变更时整体替换（旧许可仍释放回旧信号量，不膨胀新桶）。 */
    private record ConcurrencyBucket(long limit, Semaphore semaphore) {
    }

    private final Map<String, WindowCounter> windows = new ConcurrentHashMap<>();
    private final Map<String, ConcurrencyBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 请求前检查（RPM / CONCURRENT；TPM 为记账式，见 {@link #recordTokens}）。
     * 命中超限 → 429 {@code RATE_LIMITED}。
     *
     * @return 本次调用持有的并发许可（须 {@link #releaseConcurrent} 释放）；未启用 CONCURRENT 规则为空表
     */
    public List<Semaphore> check(ClientEntity client, String routeId) {
        List<Semaphore> acquired = new ArrayList<>();
        try {
            for (RateRuleSpec rule : enabledRules(client, routeId)) {
                switch (rule.limitType() == null ? "" : rule.limitType()) {
                    case "RPM" -> checkWindow(client.getClientId(), rule, 1);
                    case "TPM" -> checkWindow(client.getClientId(), rule, 0);
                    case "CONCURRENT" -> acquireConcurrent(client.getClientId(), rule, acquired);
                    default -> {
                        // 未知类型不阻断（配置宽容）
                    }
                }
            }
            return acquired;
        } catch (NexusException e) {
            acquired.forEach(Semaphore::release);
            throw e;
        }
    }

    /** TPM 记账（响应完成后回填 tokens；超限从下一请求开始拒绝）。 */
    public void recordTokens(ClientEntity client, String routeId, long totalTokens) {
        if (totalTokens <= 0) {
            return;
        }
        for (RateRuleSpec rule : enabledRules(client, routeId)) {
            if ("TPM".equals(rule.limitType())) {
                windowCounter(client.getClientId(), rule).count().add(totalTokens);
            }
        }
    }

    /** 释放并发许可（finally 中调用）。 */
    public void releaseConcurrent(List<Semaphore> acquired) {
        if (acquired == null) {
            return;
        }
        acquired.forEach(Semaphore::release);
    }

    private void checkWindow(String clientId, RateRuleSpec rule, long increment) {
        WindowCounter counter = windowCounter(clientId, rule);
        if (increment > 0) {
            counter.count().add(increment);
        }
        if (counter.count().sum() > rule.limit() + rule.burst()) {
            throw NexusException.build(NexmuxStatusCode.RATE_LIMITED,
                    "rate limited: " + rule.name() + " (" + rule.limitType() + ")");
        }
    }

    private void acquireConcurrent(String clientId, RateRuleSpec rule, List<Semaphore> acquired) {
        String key = clientId + "|" + rule.name();
        long limit = Math.max(rule.limit(), 1);
        // 限额变更热生效（免重启）：桶限额与规则不一致时整体换新；换桶瞬间旧许可未归还，
        // 短暂超发可接受，旧许可释放回旧信号量、不计入新桶
        ConcurrencyBucket bucket = buckets.compute(key, (k, existing) ->
                existing != null && existing.limit() == limit
                        ? existing : new ConcurrencyBucket(limit, new Semaphore((int) limit)));
        if (!bucket.semaphore().tryAcquire()) {
            throw NexusException.build(NexmuxStatusCode.RATE_LIMITED,
                    "concurrent limit reached: " + rule.name());
        }
        acquired.add(bucket.semaphore());
    }

    /** 分钟窗口（过期自动新建）。 */
    private WindowCounter windowCounter(String clientId, RateRuleSpec rule) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = clientId + "|" + rule.name() + "|" + rule.limitType();
        WindowCounter counter = windows.get(key);
        if (counter == null || counter.minuteEpoch() != minute) {
            counter = new WindowCounter(minute, new LongAdder());
            windows.put(key, counter);
        }
        return counter;
    }

    /** 客户端对该路由生效的启用规则。 */
    static List<RateRuleSpec> enabledRules(ClientEntity client, String routeId) {
        String spec = client.getRateLimitSpec();
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        List<RateRuleSpec> rules = new ArrayList<>();
        try {
            for (JsonNode node : NexmuxJson.read(spec, JsonNode.class)) {
                if (node.has("status") && !"enabled".equals(node.get("status").asText())) {
                    continue;
                }
                boolean scopeAll = !node.has("scopeAll") || node.get("scopeAll").asBoolean(true);
                List<String> scopeRoutes = new ArrayList<>();
                if (node.has("scopeRoutes") && node.get("scopeRoutes").isArray()) {
                    node.get("scopeRoutes").forEach(r -> scopeRoutes.add(r.asText()));
                }
                if (!scopeAll && !scopeRoutes.contains(routeId)) {
                    continue;
                }
                rules.add(new RateRuleSpec(
                        text(node, "name"), text(node, "limitType"),
                        node.has("limit") ? node.get("limit").asLong(0) : 0,
                        node.has("burst") ? node.get("burst").asLong(0) : 0,
                        scopeAll, List.copyOf(scopeRoutes)));
            }
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return rules;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
