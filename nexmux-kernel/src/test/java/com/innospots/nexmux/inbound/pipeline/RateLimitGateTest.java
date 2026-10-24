package com.innospots.nexmux.inbound.pipeline;

import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitGateTest {

    private RateLimitGate gate;

    @BeforeEach
    void setUp() {
        gate = new RateLimitGate();
    }

    private static ClientEntity client(String rateLimitSpec) {
        ClientEntity client = new ClientEntity();
        client.setClientId("cl-1");
        client.setRateLimitSpec(rateLimitSpec);
        return client;
    }

    @Test
    void noSpecMeansUnlimited() {
        assertThat(gate.check(client(null), "rt-1")).isEmpty();
    }

    @Test
    void rpmRejectsBeyondLimitPlusBurst() {
        ClientEntity client = client("[{\"name\":\"r1\",\"limitType\":\"RPM\",\"limit\":2,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        gate.check(client, "rt-1");
        gate.check(client, "rt-1");

        assertThatThrownBy(() -> gate.check(client, "rt-1"))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.RATE_LIMITED.fullCode()));
    }

    @Test
    void burstAllowsShortSpike() {
        ClientEntity client = client("[{\"name\":\"r1\",\"limitType\":\"RPM\",\"limit\":1,\"burst\":2,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        gate.check(client, "rt-1");
        gate.check(client, "rt-1");
        gate.check(client, "rt-1");

        assertThatThrownBy(() -> gate.check(client, "rt-1")).isInstanceOf(NexusException.class);
    }

    @Test
    void scopedRuleSkipsOtherRoutes() {
        ClientEntity client = client("[{\"name\":\"r1\",\"limitType\":\"RPM\",\"limit\":0,"
                + "\"scopeAll\":false,\"scopeRoutes\":[\"rt-9\"],\"status\":\"enabled\"}]");

        // 作用于 rt-9，对 rt-1 不生效
        assertThat(gate.check(client, "rt-1")).isEmpty();
        assertThatThrownBy(() -> gate.check(client, "rt-9")).isInstanceOf(NexusException.class);
    }

    @Test
    void tpmEnforcedFromNextRequestAfterRecording() {
        ClientEntity client = client("[{\"name\":\"t1\",\"limitType\":\"TPM\",\"limit\":100,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        gate.recordTokens(client, "rt-1", 150);

        assertThatThrownBy(() -> gate.check(client, "rt-1")).isInstanceOf(NexusException.class);
    }

    @Test
    void concurrentLimitBlocksAndReleaseRestores() {
        ClientEntity client = client("[{\"name\":\"c1\",\"limitType\":\"CONCURRENT\",\"limit\":1,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        List<java.util.concurrent.Semaphore> acquired = gate.check(client, "rt-1");
        assertThat(acquired).isNotEmpty();

        assertThatThrownBy(() -> gate.check(client, "rt-1")).isInstanceOf(NexusException.class);

        gate.releaseConcurrent(acquired);
        assertThat(gate.check(client, "rt-1")).isNotEmpty();
    }

    @Test
    void concurrentLimitChangeTakesEffectWithoutRestart() {
        // 限额热更新：Console 把 CONCURRENT 从 1 调到 2 后，新限额立即生效（旧实现信号量缓存旧限额）
        ClientEntity limitOne = client("[{\"name\":\"c1\",\"limitType\":\"CONCURRENT\",\"limit\":1,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        gate.check(limitOne, "rt-1");
        assertThatThrownBy(() -> gate.check(limitOne, "rt-1")).isInstanceOf(NexusException.class);

        ClientEntity limitTwo = client("[{\"name\":\"c1\",\"limitType\":\"CONCURRENT\",\"limit\":2,"
                + "\"scopeAll\":true,\"status\":\"enabled\"}]");
        assertThat(gate.check(limitTwo, "rt-1")).hasSize(1);
    }

    @Test
    void disabledRuleIgnored() {
        ClientEntity client = client("[{\"name\":\"r1\",\"limitType\":\"RPM\",\"limit\":0,"
                + "\"scopeAll\":true,\"status\":\"disabled\"}]");

        assertThat(gate.check(client, "rt-1")).isEmpty();
    }
}
