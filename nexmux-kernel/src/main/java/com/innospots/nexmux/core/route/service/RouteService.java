package com.innospots.nexmux.core.route.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.request.RouteCreateRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePageRequest;
import com.innospots.nexmux.core.route.domain.request.RoutePublishRequest;
import com.innospots.nexmux.core.route.domain.request.RouteUpdateRequest;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RouteService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final RouteDao routeDao;
    private final ConfigAuditService configAuditService;
    private final DependencyImpactAnalyzer dependencyImpactAnalyzer;

    public PageResult<RouteVo> page(RoutePageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<RouteEntity>();
        long total = routeDao.selectCount(wrapper);
        var records = routeDao.selectList(wrapper.orderByDesc(RouteEntity::getCreatedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public RouteVo get(String routeId) {
        RouteEntity entity = require(routeId);
        return toVo(entity);
    }

    public RouteVo create(RouteCreateRequest request) {
        if (routeDao.selectCount(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getAlias, request.alias())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "route alias exists");
        }
        RouteEntity entity = new RouteEntity();
        entity.setAlias(request.alias());
        entity.setDisplayName(request.displayName());
        entity.setStatus(STATUS_DRAFT);
        RouteSpec spec = defaultSpec(request.alias());
        entity.setRouteSpec(NexmuxJson.write(spec));
        routeDao.insert(entity);
        return toVo(entity);
    }

    public RouteVo updateDraft(String routeId, RouteUpdateRequest request) {
        RouteEntity entity = require(routeId);
        entity.setDisplayName(request.displayName());
        if (request.routeSpec() != null) {
            entity.setRouteSpec(NexmuxJson.write(request.routeSpec()));
        }
        routeDao.updateById(entity);
        return toVo(entity);
    }

    public RouteVo publish(String routeId, RoutePublishRequest request) {
        RouteEntity entity = require(routeId);
        dependencyImpactAnalyzer.requireSafe(
                dependencyImpactAnalyzer.analyzeDisable("ROUTE", routeId));
        entity.setPublishedRouteSpec(entity.getRouteSpec());
        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(request.operatorId());
        routeDao.updateById(entity);
        configAuditService.record(
                "ROUTE",
                routeId,
                "PUBLISH",
                request.changeSummary(),
                entity.getPublishedRouteSpec());
        return toVo(entity);
    }

    private RouteEntity require(String routeId) {
        RouteEntity entity = routeDao.selectById(routeId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "route not found");
        }
        return entity;
    }

    private RouteVo toVo(RouteEntity entity) {
        return new RouteVo(
                entity.getRouteId(),
                entity.getAlias(),
                entity.getDisplayName(),
                entity.getStatus(),
                NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class),
                entity.getPublishedAt());
    }

    private static RouteSpec defaultSpec(String alias) {
        return new RouteSpec(alias, "openai-compatible", "FIXED", List.of(), true, 1);
    }
}
