package com.innospots.nexmux.console.endpoint.provider;

import com.innospots.nexus.base.domain.response.R;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ProviderEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/nexmux/providers");
    }

    @Test
    void pageWrapsResponse() throws Exception {
        Method page = ProviderEndpoint.class.getMethod("page", Long.class, Long.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        ParameterizedType returnType = (ParameterizedType) page.getGenericReturnType();
        assertThat(returnType.getRawType()).isEqualTo(R.class);
    }
}
