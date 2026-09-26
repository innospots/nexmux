package com.innospots.nexmux.core.route.domain.vo;

import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.time.LocalDateTime;

public record RouteVo(
        String routeId,
        String alias,
        String displayName,
        String status,
        RouteSpec routeSpec,
        LocalDateTime publishedAt) {
}
