package com.innospots.nexmux.core.model.domain.entity;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelEntityContractTest {

    @Test
    void declaresBindingAndPricingColumns() throws Exception {
        assertThat(ModelEntity.class.getDeclaredField("extraBindings").getAnnotation(Column.class))
                .isNotNull();
        assertThat(ModelEntity.class.getDeclaredField("pricingSpec").getAnnotation(Column.class))
                .isNotNull();
    }

    @Test
    void avoidsJsonSuffixColumnNames() {
        assertThat(Arrays.stream(ModelEntity.class.getDeclaredFields()))
                .map(Field::getName)
                .noneMatch(name -> name.endsWith("Json"));
    }
}
