package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexmux.core.access.domain.request.ClientTemplateCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateUpdateRequest;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTemplateEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ClientTemplateEndpoint.class.getAnnotation(Path.class).value())
                .isEqualTo("/api/d/client-templates");
        assertThat(ClientTemplateEndpoint.class.getAnnotation(Tag.class).name())
                .isEqualTo("NexmuxClientTemplate");
        assertThat(ClientTemplateEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void declaresCrudAndSubResources() throws Exception {
        Method page = ClientTemplateEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientTemplatePage");
        assertThat(page.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.access.ClientTemplateListResult.class);

        // UI 契约：详情按稳定业务键直出展示记录（/api/d/client-templates/{code}）
        Method detail = ClientTemplateEndpoint.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(Path.class).value()).isEqualTo("/{templateCode}");
        assertThat(detail.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientTemplateGetByCode");
        assertThat(detail.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.access.ClientTemplateRecord.class);

        Method create = ClientTemplateEndpoint.class.getMethod("create", ClientTemplateCreateRequest.class);
        assertThat(create.getAnnotation(POST.class)).isNotNull();

        Method update = ClientTemplateEndpoint.class.getMethod("update", String.class, ClientTemplateUpdateRequest.class);
        assertThat(update.getAnnotation(PUT.class)).isNotNull();
        assertThat(update.getAnnotation(Path.class).value()).isEqualTo("/{templateId}");

        Method disable = ClientTemplateEndpoint.class.getMethod("disable", String.class);
        assertThat(disable.getAnnotation(Path.class).value()).isEqualTo("/{templateId}/disable");

        Method enable = ClientTemplateEndpoint.class.getMethod("enable", String.class);
        assertThat(enable.getAnnotation(POST.class)).isNotNull();
        assertThat(enable.getAnnotation(Path.class).value()).isEqualTo("/{templateId}/enable");

        Method createdClients = ClientTemplateEndpoint.class.getMethod("createdClients", String.class);
        assertThat(createdClients.getAnnotation(GET.class)).isNotNull();
        assertThat(createdClients.getAnnotation(Path.class).value()).isEqualTo("/{templateCode}/created-clients");
        assertThat(createdClients.getAnnotation(Operation.class).operationId())
                .isEqualTo("nexmuxClientTemplateCreatedClients");
    }
}
