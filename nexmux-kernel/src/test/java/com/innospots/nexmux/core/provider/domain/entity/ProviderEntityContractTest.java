package com.innospots.nexmux.core.provider.domain.entity;

import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEntityContractTest {

    @Test
    void declaresMuxProviderTable() {
        assertThat(ProviderEntity.TABLE_NAME).isEqualTo("mux_provider");
        Table table = ProviderEntity.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo(ProviderEntity.TABLE_NAME);
        assertThat(ProviderEntity.class.getAnnotation(Entity.class)).isNotNull();
    }

    @Test
    void extendsBaseEntity() {
        assertThat(BaseEntity.class).isAssignableFrom(ProviderEntity.class);
    }

    @Test
    void declaresVendorAttributionFields() throws Exception {
        assertField("vendorCode");
        assertField("sourceVendorCode");
        assertField("iconUri");
    }

    @Test
    void overridesIdPrefix() {
        assertThat(new ProviderEntity().idPrefix()).isEqualTo("pv");
    }

    private static void assertField(String name) throws Exception {
        Field field = ProviderEntity.class.getDeclaredField(name);
        assertThat(field.getAnnotation(Column.class)).isNotNull();
    }
}
