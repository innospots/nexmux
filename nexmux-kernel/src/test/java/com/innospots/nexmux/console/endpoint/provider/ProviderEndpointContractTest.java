package com.innospots.nexmux.console.endpoint.provider;

import com.innospots.nexus.base.domain.response.R;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ProviderEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/d/providers");
        assertThat(ProviderEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxProvider");
        assertThat(ProviderEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void pageWrapsResponse() throws Exception {
        Method page = ProviderEndpoint.class.getMethod("page", Long.class, Long.class,
                String.class, String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderPage");
        ParameterizedType returnType = (ParameterizedType) page.getGenericReturnType();
        assertThat(returnType.getRawType()).isEqualTo(R.class);
    }

    @Test
    void declaresGetByCode() throws Exception {
        Method get = ProviderEndpoint.class.getMethod("getByCode", String.class);
        assertThat(get.getAnnotation(GET.class)).isNotNull();
        assertThat(get.getAnnotation(Path.class).value()).isEqualTo("/by-code/{providerCode}");
        assertThat(get.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderGetByCode");
    }

    @Test
    void declaresDisable() throws Exception {
        Method disable = ProviderEndpoint.class.getMethod("disable", String.class);
        assertThat(disable.getAnnotation(jakarta.ws.rs.POST.class)).isNotNull();
        assertThat(disable.getAnnotation(Path.class).value()).isEqualTo("/{providerId}/disable");
        assertThat(disable.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderDisable");
    }

    @Test
    void declaresPullModels() throws Exception {
        Method pull = ProviderEndpoint.class.getMethod("pullModels", String.class);
        assertThat(pull.getAnnotation(jakarta.ws.rs.POST.class)).isNotNull();
        assertThat(pull.getAnnotation(Path.class).value()).isEqualTo("/{providerId}/pull-models");
        assertThat(pull.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderPullModels");
    }

    @Test
    void connectionTestReturnsRichResult() throws Exception {
        Method test = ProviderEndpoint.class.getMethod("connectionTest", String.class);
        ParameterizedType returnType = (ParameterizedType) test.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()[0])
                .isEqualTo(com.innospots.nexmux.core.provider.domain.vo.ConnectionTestVo.class);
    }
}
