package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ModelEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/api/d/models");
        assertThat(ModelEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxModel");
        assertThat(ModelEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();
    }

    @Test
    void pageAcceptsFilters() throws Exception {
        Method page = ModelEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelPage");
    }

    @Test
    void declaresGetByCode() throws Exception {
        Method get = ModelEndpoint.class.getMethod("getByCode", String.class);
        assertThat(get.getAnnotation(GET.class)).isNotNull();
        assertThat(get.getAnnotation(Path.class).value()).isEqualTo("/by-code/{modelCode: .+}");
        assertThat(get.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelGetByCode");
    }

    @Test
    void declaresUpdate() throws Exception {
        Method update = ModelEndpoint.class.getMethod("update", String.class, ModelUpdateRequest.class);
        assertThat(update.getAnnotation(PUT.class)).isNotNull();
        assertThat(update.getAnnotation(Path.class).value()).isEqualTo("/{modelId}");
        assertThat(update.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelUpdate");
    }

    @Test
    void declaresDisable() throws Exception {
        Method disable = ModelEndpoint.class.getMethod("disable", String.class);
        assertThat(disable.getAnnotation(POST.class)).isNotNull();
        assertThat(disable.getAnnotation(Path.class).value()).isEqualTo("/{modelId}/disable");
        assertThat(disable.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelDisable");
    }

    @Test
    void getByCodeWrapsDetailVo() throws Exception {
        Method get = ModelEndpoint.class.getMethod("getByCode", String.class);
        ParameterizedType returnType = (ParameterizedType) get.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()[0])
                .isEqualTo(com.innospots.nexmux.core.model.domain.vo.ModelDetailVo.class);
    }
}
