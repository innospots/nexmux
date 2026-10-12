package com.innospots.nexmux.core.provider.domain.request;

import java.lang.reflect.RecordComponent;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCreateRequestOpenApiContractTest {

    @Test
    void declaresSchemaOnTypeAndFields() throws Exception {
        Schema typeSchema = ProviderCreateRequest.class.getAnnotation(Schema.class);
        assertThat(typeSchema.name()).isEqualTo("ProviderCreateRequest");
        assertThat(typeSchema.description()).isEqualTo("创建模型服务提供方");
        var constructor = ProviderCreateRequest.class.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                String.class,
                String.class);
        assertThat(constructor.getParameters()[0].getAnnotation(Schema.class).description()).isEqualTo("唯一编码");
    }

    @Test
    void recordComponentsMatchOpenApiFields() {
        assertThat(ProviderCreateRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "providerCode",
                        "displayName",
                        "providerType",
                        "defaultProtocol",
                        "vendorCode",
                        "sourceVendorCode",
                        "iconUri",
                        "baseUrl",
                        "region",
                        "timeoutMs",
                        "apiKey",
                        "description");
    }
}
