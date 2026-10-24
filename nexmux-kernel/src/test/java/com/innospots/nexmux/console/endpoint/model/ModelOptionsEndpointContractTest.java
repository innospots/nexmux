package com.innospots.nexmux.console.endpoint.model;

import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelOptionsEndpointContractTest {

    @Test
    void declaresOptionsContract() throws Exception {
        // UI 契约：{items:[{label,value}]} 直出，不包 R（路由编辑「添加模型」Select）
        assertThat(ModelOptionsEndpoint.class.getAnnotation(Path.class).value())
                .isEqualTo("/api/d/model-options");
        assertThat(ModelOptionsEndpoint.class.getAnnotation(Tag.class).name()).isEqualTo("NexmuxModel");
        assertThat(ModelOptionsEndpoint.class.getAnnotation(NexusAuthenticatedApi.class)).isNotNull();

        Method options = ModelOptionsEndpoint.class.getMethod("options");
        assertThat(options.getAnnotation(GET.class)).isNotNull();
        assertThat(options.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxModelOptions");
        assertThat(options.getReturnType())
                .isEqualTo(ModelOptionsEndpoint.OptionsResult.class);
    }
}
