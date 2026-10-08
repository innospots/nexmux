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
import com.innospots.nexmux.core.route.domain.vo.RouteDetailVo;
import com.innospots.nexmux.core.route.domain.vo.RouteVo;
import com.innospots.nexmux.core.shared.analysis.DependencyImpactAnalyzer;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.core.route.domain.value.RouteCandidate;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RouteService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final RouteDao routeDao;
    private final ConfigAuditService configAuditService;
    private final DependencyImpactAnalyzer dependencyImpactAnalyzer;

    public PageResult<RouteVo> page(RoutePageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<RouteEntity>();
        if (request.status() != null && !request.status().isBlank()) {
            wrapper.eq(RouteEntity::getStatus, request.status());
        }
        // type（候选数）与 tags（JSON）在 route_spec CLOB 内，SQL 不可表达：
        // P0 数据量小，内存过滤 + 内存分页（规格见 nexmux-ui-data-api-design §6.3）
        List<RouteEntity> matched = routeDao.selectList(wrapper.orderByDesc(RouteEntity::getCreatedAt))
                .stream()
                .filter(e -> matchesType(e, request.type()))
                .filter(e -> matchesTags(e, request.tags()))
                .filter(e -> matchesModel(e, request.model()))
                .toList();
        long from = Math.min((pageNo - 1) * pageSize, matched.size());
        long to = Math.min(from + pageSize, matched.size());
        var records = matched.subList((int) from, (int) to).stream().map(this::toVo).toList();
        return PageResult.of(records, pageNo, pageSize, matched.size());
    }

    /**
     * 全量查询（Console 列表页关联统计用；P0 数据量小，不分页）。
     */
    public List<RouteVo> listAll() {
        return routeDao.selectList(new LambdaQueryWrapper<RouteEntity>()
                        .orderByAsc(RouteEntity::getCreatedAt))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public RouteVo get(String routeId) {
        return toVo(require(routeId));
    }

    /**
     * 按稳定业务键查询详情（含草稿与发布快照对照）。
     */
    public RouteDetailVo getByCode(String routeCode) {
        RouteEntity entity = routeDao.selectOne(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getRouteCode, routeCode));
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "route not found: " + routeCode);
        }
        return new RouteDetailVo(
                entity.getRouteId(),
                entity.getRouteCode(),
                entity.getAlias(),
                entity.getDisplayName(),
                entity.getStatus(),
                NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class),
                NexmuxJson.read(entity.getPublishedRouteSpec(), RouteSpec.class),
                entity.getPublishedAt(),
                entity.getPublishedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public RouteVo create(RouteCreateRequest request) {
        if (routeDao.selectCount(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getRouteCode, request.routeCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "route code exists");
        }
        if (routeDao.selectCount(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getAlias, request.alias())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "route alias exists");
        }
        RouteEntity entity = new RouteEntity();
        entity.setRouteCode(request.routeCode());
        entity.setAlias(request.alias());
        entity.setDisplayName(request.displayName());
        entity.setStatus(STATUS_DRAFT);
        entity.setRouteSpec(NexmuxJson.write(specOf(request)));
        routeDao.insert(entity);
        return toVo(entity);
    }

    public RouteVo updateDraft(String routeId, RouteUpdateRequest request) {
        RouteEntity entity = require(routeId);
        if (request.displayName() != null) {
            entity.setDisplayName(request.displayName());
        }
        if (request.routeSpec() != null) {
            entity.setRouteSpec(NexmuxJson.write(request.routeSpec()));
        }
        routeDao.updateById(entity);
        return toVo(entity);
    }

    /**
     * 启用：仅 DISABLED → PUBLISHED；草稿须先发布。
     */
    public RouteVo enable(String routeId) {
        RouteEntity entity = require(routeId);
        if (!STATUS_DISABLED.equals(entity.getStatus())) {
            throw NexusException.build(
                    NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "only a disabled route can be enabled; publish drafts instead");
        }
        entity.setStatus(STATUS_PUBLISHED);
        routeDao.updateById(entity);
        configAuditService.record("ROUTE", routeId, "ENABLE", null,
                "{\"before\":null,\"after\":" + entity.getPublishedRouteSpec() + "}");
        return toVo(entity);
    }

    /**
     * 停用：仅 PUBLISHED → DISABLED；草稿无运行时流量，无需停用。
     */
    public RouteVo disable(String routeId) {
        RouteEntity entity = require(routeId);
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw NexusException.build(
                    NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "only a published route can be disabled");
        }
        dependencyImpactAnalyzer.requireSafe(
                dependencyImpactAnalyzer.analyzeDisable("ROUTE", routeId));
        entity.setStatus(STATUS_DISABLED);
        routeDao.updateById(entity);
        configAuditService.record("ROUTE", routeId, "DISABLE", null,
                "{\"before\":" + entity.getPublishedRouteSpec() + ",\"after\":null}");
        return toVo(entity);
    }

    /**
     * 删除路由；存在客户端授权引用时拒绝（前置检查，与停用同一影响分析）。
     */
    public void delete(String routeId) {
        RouteEntity entity = require(routeId);
        dependencyImpactAnalyzer.requireSafe(
                dependencyImpactAnalyzer.analyzeDisable("ROUTE", routeId));
        configAuditService.record("ROUTE", routeId, "DELETE", null,
                "{\"before\":" + entity.getRouteSpec() + ",\"after\":null}");
        routeDao.deleteById(entity);
    }

    public RouteVo publish(String routeId, RoutePublishRequest request) {
        RouteEntity entity = require(routeId);
        dependencyImpactAnalyzer.requireSafe(
                dependencyImpactAnalyzer.analyzeDisable("ROUTE", routeId));
        String before = entity.getPublishedRouteSpec() == null ? "null" : entity.getPublishedRouteSpec();
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
                "{\"before\":" + before + ",\"after\":" + entity.getPublishedRouteSpec() + "}",
                request.operatorId());
        return toVo(entity);
    }

    private boolean matchesType(RouteEntity entity, String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        RouteSpec spec = NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class);
        int count = spec == null ? 0 : spec.candidates().size();
        return switch (type) {
            case "single" -> count == 1;
            case "pool" -> count > 1;
            default -> true; // conditional 等 P0 未产出类型不过滤
        };
    }

    private boolean matchesTags(RouteEntity entity, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        RouteSpec spec = NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class);
        List<String> own = spec == null ? List.of() : spec.tags();
        return tags.stream().allMatch(own::contains);
    }

    private boolean matchesModel(RouteEntity entity, String model) {
        if (model == null || model.isBlank()) {
            return true;
        }
        RouteSpec spec = NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class);
        List<RouteCandidate> candidates = spec == null ? List.of() : spec.candidates();
        return candidates.stream().anyMatch(c -> model.equals(c.modelCode()));
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
                entity.getRouteCode(),
                entity.getAlias(),
                entity.getDisplayName(),
                entity.getStatus(),
                NexmuxJson.read(entity.getRouteSpec(), RouteSpec.class),
                entity.getPublishedAt());
    }

    private static RouteSpec specOf(RouteCreateRequest request) {
        return new RouteSpec(
                request.alias(),
                request.path() == null ? "/v1/chat/completions" : request.path(),
                request.strategyType() == null ? "FIXED" : request.strategyType(),
                request.models() == null ? List.of() : request.models(),
                request.healthFilter() == null || request.healthFilter(),
                1,
                request.description(),
                request.protocol(),
                request.tags(),
                request.stickySession());
    }
}
