package com.innospots.nexmux.console.endpoint.settings;

import com.innospots.nexus.core.openapi.NexusAuthenticatedApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceSettingsEndpointContractTest {

    @Test
    void declaresApiBasePath() {
        assertThat(ServiceSettingsEndpoint.class.getAnnotation(Path.class).value())
                .isEqualTo("/api/d/service-settings");
        assertThat(ServiceSettingsEndpoint.class.getAnnotation(Tag.class).name())
                .isEqualTo("NexmuxServiceSettings");
        assertThat(ServiceSettingsEndpoint.class.getAnnotation(NexusAuthenticatedApi.class))
                .isNotNull();
    }

    @Test
    void declaresGetAndPutContract() throws Exception {
        // UI 契约：GET 直出扁平设置对象（不包 R）；PUT 整包保存后返回全量
        Method get = ServiceSettingsEndpoint.class.getMethod("get");
        assertThat(get.getAnnotation(GET.class)).isNotNull();
        assertThat(get.getAnnotation(Operation.class).operationId())
                .isEqualTo("nexmuxServiceSettingsGet");
        assertThat(get.getReturnType()).isEqualTo(Map.class);

        Method save = ServiceSettingsEndpoint.class.getMethod("save", Map.class);
        assertThat(save.getAnnotation(PUT.class)).isNotNull();
        assertThat(save.getAnnotation(Operation.class).operationId())
                .isEqualTo("nexmuxServiceSettingsSave");
        assertThat(save.getReturnType()).isEqualTo(Map.class);
    }
}
