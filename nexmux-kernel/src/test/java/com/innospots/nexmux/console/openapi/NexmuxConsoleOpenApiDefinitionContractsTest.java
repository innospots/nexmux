package com.innospots.nexmux.console.openapi;

import jakarta.ws.rs.Path;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import com.innospots.nexus.core.openapi.NexusOpenApiSecurityNames;

import static org.assertj.core.api.Assertions.assertThat;

class NexmuxConsoleOpenApiDefinitionContractsTest {

    @Test
    void definitionCarriesPathForJaxRsScanner() {
        assertThat(NexmuxConsoleOpenApiDefinition.class.getAnnotation(Path.class).value()).isEqualTo("/api/nexmux");
        assertThat(NexmuxConsoleOpenApiDefinition.class.getAnnotation(OpenAPIDefinition.class)).isNotNull();
        assertThat(NexmuxConsoleOpenApiDefinition.class.getAnnotation(SecurityScheme.class).securitySchemeName())
                .isEqualTo(NexusOpenApiSecurityNames.BEARER_AUTH);
    }

    @Test
    void definitionDeclaresNexmuxTags() {
        Tag[] tags = NexmuxConsoleOpenApiDefinition.class.getAnnotation(OpenAPIDefinition.class).tags();
        assertThat(tags).extracting(Tag::name)
                .containsExactly(
                        "NexmuxProvider",
                        "NexmuxModel",
                        "NexmuxRoute",
                        "NexmuxClient",
                        "NexmuxOps",
                        "NexmuxDebug");
    }
}
