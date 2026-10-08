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
    void pageReturnsUiListContract() throws Exception {
        // /api/d 列表契约：{items,total,filters} 直出，不包 R（nexmux-ui-missing-api 对接约定）
        Method page = ModelEndpoint.class.getMethod(
                "page", Long.class, Long.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelPage");
        assertThat(page.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.model.ModelListResult.class);
    }

    @Test
    void declaresDetailByCode() throws Exception {
        // UI 契约：详情按稳定业务键直出展示记录（/api/d/models/{code}，~ 替代 /）
        Method detail = ModelEndpoint.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(GET.class)).isNotNull();
        assertThat(detail.getAnnotation(Path.class).value()).isEqualTo("/{modelCode}");
        assertThat(detail.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelGetByCode");
        assertThat(detail.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.model.ModelRecord.class);
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

}
