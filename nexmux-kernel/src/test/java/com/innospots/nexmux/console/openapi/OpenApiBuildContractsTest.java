package com.innospots.nexmux.console.openapi;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiBuildContractsTest {

    @Test
    void buildGeneratesNexmuxOpenApiSpec() throws Exception {
        Path spec = Path.of("target/classes/META-INF/nexus-openapi/nexmux-kernel.yaml");
        assertThat(spec).exists();
        String yaml = Files.readString(spec);
        assertThat(yaml).contains("Nexmux Console API");
        assertThat(yaml).contains("/api/d/providers");
        assertThat(yaml).contains("/api/d/models");
        assertThat(yaml).contains("/api/d/routes");
        assertThat(yaml).contains("/api/d/clients");
        assertThat(yaml).contains("operationId: nexmuxProviderPage");
        assertThat(yaml).contains("operationId: nexmuxRoutePublish");
        assertThat(yaml).contains("name: NexmuxProvider");
        assertThat(yaml).contains("name: NexmuxClient");
        assertThat(yaml).contains("bearerAuth");
        assertThat(yaml).contains("ProviderCreateRequest:");
        assertThat(yaml).contains("providerCode:");
        assertThat(yaml).contains("ProviderVo:");
        assertThat(yaml).contains("RouteSpec:");
        assertThat(yaml).contains("ClientCreateResultVo:");
    }
}
