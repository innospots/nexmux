package com.innospots.nexmux.core.route.domain.request;

import com.innospots.nexmux.core.route.domain.value.RouteSpec;

public record RouteUpdateRequest(String displayName, RouteSpec routeSpec) {
}
