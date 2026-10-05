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
        assertThat(ClientEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/nexmux/clients");
        assertThat(ClientEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxClient");
        assertThat(ClientEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void pageAcceptsFilters() throws Exception {
        Method page = ClientEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientPage");
    }

    @Test
    void declaresGetByCode() throws Exception {
        Method get = ClientEndpoint.class.getMethod("getByCode", String.class);
        assertThat(get.getAnnotation(GET.class)).isNotNull();
        assertThat(get.getAnnotation(Path.class).value()).isEqualTo("/by-code/{clientCode}");
        assertThat(get.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxClientGetByCode");
        ParameterizedType returnType = (ParameterizedType) get.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()[0])
                .isEqualTo(com.innospots.nexmux.core.access.domain.vo.ClientDetailVo.class);
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
}
