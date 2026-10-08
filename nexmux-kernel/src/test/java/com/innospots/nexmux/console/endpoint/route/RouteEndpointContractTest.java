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
    void pageReturnsUiListContract() throws Exception {
        // /api/d 列表契约：{items,total,filters} 直出，不包 R（nexmux-ui-missing-api 对接约定）
        Method page = RouteEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRoutePage");
        assertThat(page.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.route.RouteListResult.class);
    }

    @Test
    void declaresDetailByCode() throws Exception {
        // UI 契约：详情按稳定业务键直出展示记录（/api/d/routes/{code}）
        Method detail = RouteEndpoint.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(GET.class)).isNotNull();
        assertThat(detail.getAnnotation(Path.class).value()).isEqualTo("/{routeCode}");
        assertThat(detail.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxRouteGetByCode");
        assertThat(detail.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.route.RouteRecord.class);
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
