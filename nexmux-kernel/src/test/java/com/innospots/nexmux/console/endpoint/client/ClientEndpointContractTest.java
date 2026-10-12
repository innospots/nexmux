package com.innospots.nexmux.console.endpoint.client;

import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ClientEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/d/clients");
        assertThat(ClientEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxClient");
        assertThat(ClientEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void pageReturnsUiListContract() throws Exception {
        // /api/d 列表契约：{items,total,filters} 直出，不包 R（nexmux-ui-missing-api 对接约定）
        Method page = ClientEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientPage");
        assertThat(page.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.access.ClientListResult.class);
    }

    @Test
    void declaresDetailByCode() throws Exception {
        // UI 契约：详情按稳定业务键直出展示记录（/api/d/clients/{code}）
        Method detail = ClientEndpoint.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(GET.class)).isNotNull();
        assertThat(detail.getAnnotation(Path.class).value()).isEqualTo("/{clientCode}");
        assertThat(detail.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientGetByCode");
        assertThat(detail.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.access.ClientRecord.class);
    }

    @Test
    void declaresEnableAndDisable() throws Exception {
        Method enable = ClientEndpoint.class.getMethod("enable", String.class);
        assertThat(enable.getAnnotation(POST.class)).isNotNull();
        assertThat(enable.getAnnotation(Path.class).value()).isEqualTo("/{clientId}/enable");

        Method disable = ClientEndpoint.class.getMethod("disable", String.class);
        assertThat(disable.getAnnotation(POST.class)).isNotNull();
        assertThat(disable.getAnnotation(Path.class).value()).isEqualTo("/{clientId}/disable");
    }

    @Test
    void declaresDelete() throws Exception {
        Method delete = ClientEndpoint.class.getMethod("delete", String.class);
        assertThat(delete.getAnnotation(jakarta.ws.rs.DELETE.class)).isNotNull();
        assertThat(delete.getAnnotation(Path.class).value()).isEqualTo("/{clientId}");
        assertThat(delete.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientDelete");
    }
}
