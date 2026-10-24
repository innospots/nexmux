package com.innospots.nexmux.console.endpoint.provider;

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
    void pageReturnsUiListContract() throws Exception {
        // /api/d 列表契约：{items,total,filters} 直出，不包 R（nexmux-ui-missing-api 对接约定）
        Method page = ProviderEndpoint.class.getMethod("page", Long.class, Long.class,
                String.class, String.class, String.class);
        assertThat(page.getAnnotation(GET.class)).isNotNull();
        assertThat(page.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderPage");
        assertThat(page.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.provider.ProviderListResult.class);
    }

    @Test
    void declaresDetailByCode() throws Exception {
        // UI 契约：详情按稳定业务键直出展示记录（/api/d/providers/{code}）
        Method detail = ProviderEndpoint.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(GET.class)).isNotNull();
        assertThat(detail.getAnnotation(Path.class).value()).isEqualTo("/{providerCode}");
        assertThat(detail.getAnnotation(Operation.class).operationId()).isEqualTo("nexmuxProviderGetByCode");
        assertThat(detail.getReturnType())
                .isEqualTo(com.innospots.nexmux.console.provider.ProviderRecord.class);
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
        assertThat(pull.getAnnotation(GET.class)).isNotNull();
        assertThat(pull.getAnnotation(Path.class).value()).isEqualTo("/{providerCode}/pull-models");
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
