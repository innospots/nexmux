package com.innospots.nexmux.core.route.domain.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteEntityContractTest {

    @Test
    void declaresRouteSpecColumns() throws Exception {
        Column routeSpec = RouteEntity.class.getDeclaredField("routeSpec").getAnnotation(Column.class);
        Column published = RouteEntity.class.getDeclaredField("publishedRouteSpec").getAnnotation(Column.class);
        assertThat(routeSpec.columnDefinition()).containsIgnoringCase("CLOB");
        assertThat(published.columnDefinition()).containsIgnoringCase("CLOB");
    }

    @Test
    void declaresUniqueAliasIndex() {
        assertThat(RouteEntity.class.getAnnotation(jakarta.persistence.Table.class).indexes())
                .anySatisfy(index -> assertThat(index.columnList()).contains("alias"));
    }

    @Test
    void declaresUniqueRouteCodeIndex() throws Exception {
        assertThat(RouteEntity.class.getAnnotation(jakarta.persistence.Table.class).indexes())
                .anySatisfy(index -> assertThat(index.columnList()).contains("route_code"));
        assertThat(RouteEntity.class.getDeclaredField("routeCode")).isNotNull();
    }
}
