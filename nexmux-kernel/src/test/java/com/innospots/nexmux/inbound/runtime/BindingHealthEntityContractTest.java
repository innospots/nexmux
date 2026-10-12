package com.innospots.nexmux.inbound.runtime;

import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BindingHealthEntityContractTest {

    @Test
    void declaresMuxBindingHealthTable() {
        assertThat(BindingHealthEntity.TABLE_NAME).isEqualTo("mux_binding_health");
        Table table = BindingHealthEntity.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo(BindingHealthEntity.TABLE_NAME);
        assertThat(BindingHealthEntity.class.getAnnotation(Entity.class)).isNotNull();
    }

    @Test
    void declaresUniqueIndexOnProbeTarget() {
        Table table = BindingHealthEntity.class.getAnnotation(Table.class);
        Index uk = Arrays.stream(table.indexes())
                .filter(i -> i.name().equals("uk_mux_binding_health_target"))
                .findFirst()
                .orElseThrow();
        assertThat(uk.unique()).isTrue();
        assertThat(uk.columnList()).isEqualTo("provider_id, provider_model_id");
    }

    @Test
    void extendsBaseEntity() {
        assertThat(BaseEntity.class).isAssignableFrom(BindingHealthEntity.class);
    }

    @Test
    void overridesIdPrefix() {
        assertThat(new BindingHealthEntity().idPrefix()).isEqualTo("bh");
    }
}
