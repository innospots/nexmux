package com.innospots.nexmux.core.route.domain.value;

import java.util.List;

public record RouteSpec(
        String alias,
        String ingress,
        String strategyType,
        List<RouteCandidate> candidates,
        boolean filterUnhealthyBindings,
        int schemaVersion) {

    public RouteSpec {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
