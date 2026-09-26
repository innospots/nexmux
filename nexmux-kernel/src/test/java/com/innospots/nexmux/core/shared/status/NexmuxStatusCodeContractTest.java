package com.innospots.nexmux.core.shared.status;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxStatusCodeContractTest {

    @Test
    void usesModuleCodeNmx() {
        assertThat(NexmuxStatusCode.RESOURCE_NOT_FOUND.module()).isEqualTo("NMX");
    }

    @Test
    void hasUniqueLocalCodesPerCategory() {
        var keys = Arrays.stream(NexmuxStatusCode.values())
                .map(code -> code.category() + ":" + code.localCode())
                .collect(Collectors.toSet());
        assertThat(keys).hasSize(NexmuxStatusCode.values().length);
    }
}
