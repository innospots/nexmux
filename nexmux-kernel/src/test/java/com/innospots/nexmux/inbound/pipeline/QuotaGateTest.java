package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaGateTest {

    private QuotaGate gate;

    @BeforeEach
    void setUp() {
        gate = new QuotaGate();
    }

    private static ClientEntity client(String quotaSpec) {
        ClientEntity client = new ClientEntity();
        client.setClientId("cl-1");
        client.setQuotaSpec(quotaSpec);
        return client;
    }

    @Test
    void noQuotaSpecMeansUnlimited() {
        assertThat(gate.check(client(null))).isEmpty();
        gate.recordRequest(client(null));
    }

    @Test
    void hardLifetimeRequestQuotaRejectsWhenExhausted() {
        ClientEntity client = client("[{\"name\":\"q1\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":3,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        gate.recordRequest(client);
        gate.recordRequest(client);
        gate.recordRequest(client);

        assertThatThrownBy(() -> gate.check(client))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()));
    }

    @Test
    void softQuotaPassesAndReportsTrigger() {
        ClientEntity client = client("[{\"name\":\"soft-q\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":1,\"mode\":\"soft\",\"status\":\"enabled\"}]");
        gate.recordRequest(client);

        assertThat(gate.check(client)).containsExactly("soft-q");
    }

    @Test
    void disabledRuleIsIgnored() {
        ClientEntity client = client("[{\"name\":\"q1\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":0,\"mode\":\"hard\",\"status\":\"disabled\"}]");

        assertThat(gate.check(client)).isEmpty();
    }

    @Test
    void tokenMetricsAccumulate() {
        ClientEntity client = client("[{\"name\":\"tq\",\"quotaType\":\"lifetime\",\"metric\":\"TOTAL_TOKEN\","
                + "\"limit\":100,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        gate.recordUsage(client, 60, 30, null);
        assertThat(gate.check(client)).isEmpty();

        gate.recordUsage(client, 10, 10, null);

        assertThatThrownBy(() -> gate.check(client))
                .isInstanceOf(NexusException.class);
    }

    @Test
    void costMetricUsesScaledLong() {
        ClientEntity client = client("[{\"name\":\"cq\",\"quotaType\":\"lifetime\",\"metric\":\"COST\","
                + "\"limit\":2,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        gate.recordUsage(client, 0, 0, new BigDecimal("1.5"));

        assertThat(gate.check(client)).isEmpty();
        gate.recordUsage(client, 0, 0, new BigDecimal("0.6"));
        assertThatThrownBy(() -> gate.check(client)).isInstanceOf(NexusException.class);
    }

    @Test
    void cyclicWindowResetsOnNextPeriod() {
        ClientEntity client = client("[{\"name\":\"dq\",\"quotaType\":\"cyclic\",\"metric\":\"REQUEST\","
                + "\"limit\":1,\"mode\":\"hard\",\"cycleUnit\":\"day\",\"status\":\"enabled\"}]");
        gate.recordRequest(client);
        assertThatThrownBy(() -> gate.check(client)).isInstanceOf(NexusException.class);

        // 窗口起点按天截断：验证窗口推算函数本身
        var rule = QuotaGate.enabledRules(client).get(0);
        assertThat(gate.windowStart(rule).getHour()).isZero();
    }

    @Test
    void cyclicWindowResetsWhenClockAdvancesPastWindow() {
        java.util.concurrent.atomic.AtomicReference<java.time.Instant> now =
                new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-24T10:30:00Z"));
        java.time.Clock clock = new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneId.of("UTC");
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public java.time.Instant instant() {
                return now.get();
            }
        };
        gate = new QuotaGate(clock);
        ClientEntity client = client("[{\"name\":\"hq\",\"quotaType\":\"cyclic\",\"metric\":\"REQUEST\","
                + "\"limit\":1,\"mode\":\"hard\",\"cycleUnit\":\"hour\",\"status\":\"enabled\"}]");
        gate.recordRequest(client);
        assertThatThrownBy(() -> gate.check(client)).isInstanceOf(NexusException.class);

        now.set(java.time.Instant.parse("2026-10-24T11:00:00Z"));

        assertThat(gate.check(client)).isEmpty();
    }

    @Test
    void resyncMergesDatabaseUsage() {
        ClientEntity client = client("[{\"name\":\"q1\",\"quotaType\":\"lifetime\",\"metric\":\"REQUEST\","
                + "\"limit\":10,\"mode\":\"hard\",\"status\":\"enabled\"}]");
        var rules = QuotaGate.enabledRules(client);
        gate.resync("cl-1", rules, java.util.Map.of("REQUEST", 9L));
        gate.recordRequest(client);

        assertThatThrownBy(() -> gate.check(client)).isInstanceOf(NexusException.class);
    }

    @Test
    void malformedQuotaSpecToleratedAsUnlimited() {
        assertThat(gate.check(client("{broken"))).isEmpty();
    }

    @Test
    void concurrentMetricRuleIsIgnoredInQuota() {
        // G-07：配额侧不再接受 CONCURRENT（并发控制归 RateLimitGate）；
        // 存量 CONCURRENT 规则忽略——即使 limit=0 hard 也不触发
        ClientEntity client = client("[{\"name\":\"c1\",\"quotaType\":\"lifetime\","
                + "\"metric\":\"CONCURRENT\",\"limit\":0,\"mode\":\"hard\",\"status\":\"enabled\"}]");

        assertThat(QuotaGate.enabledRules(client)).isEmpty();
        assertThat(gate.check(client)).isEmpty();
    }
}
