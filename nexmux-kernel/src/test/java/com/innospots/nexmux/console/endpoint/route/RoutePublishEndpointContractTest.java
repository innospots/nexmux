package com.innospots.nexmux.console.endpoint.route;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePublishEndpointContractTest {

    @Test
    void declaresPublishSubResource() throws Exception {
        Method publish = RouteEndpoint.class.getMethod("publish", String.class,
                com.innospots.nexmux.core.route.domain.request.RoutePublishRequest.class);
        assertThat(publish.getAnnotation(POST.class)).isNotNull();
        assertThat(publish.getAnnotation(Path.class).value()).isEqualTo("/{routeId}/publish");
    }
}
