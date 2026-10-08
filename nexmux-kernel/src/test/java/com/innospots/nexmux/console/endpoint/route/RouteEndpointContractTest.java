package com.innospots.nexmux.console.endpoint.route;

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

class RouteEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(RouteEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/d/routes");
        assertThat(RouteEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxRoute");
        assertThat(RouteEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void pageAcceptsFilters() throws Exception {
        Method page = RouteEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRoutePage");
    }

    @Test
    void declaresGetByCode() throws Exception {
        Method get = RouteEndpoint.class.getMethod("getByCode", String.class);
        assertThat(get.getAnnotation(GET.class)).isNotNull();
        assertThat(get.getAnnotation(Path.class).value()).isEqualTo("/by-code/{routeCode}");
        assertThat(get.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRouteGetByCode");
        ParameterizedType returnType = (ParameterizedType) get.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()[0])
                .isEqualTo(com.innospots.nexmux.core.route.domain.vo.RouteDetailVo.class);
    }

    @Test
    void declaresEnableAndDisable() throws Exception {
        Method enable = RouteEndpoint.class.getMethod("enable", String.class);
        assertThat(enable.getAnnotation(POST.class)).isNotNull();
        assertThat(enable.getAnnotation(Path.class).value()).isEqualTo("/{routeId}/enable");
        assertThat(enable.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRouteEnable");

        Method disable = RouteEndpoint.class.getMethod("disable", String.class);
        assertThat(disable.getAnnotation(POST.class)).isNotNull();
        assertThat(disable.getAnnotation(Path.class).value()).isEqualTo("/{routeId}/disable");
        assertThat(disable.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRouteDisable");
    }
}
