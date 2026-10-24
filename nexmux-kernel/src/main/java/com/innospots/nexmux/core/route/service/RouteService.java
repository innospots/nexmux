package com.innospots.nexmux.core.route.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import com.innospots.nexmux.core.route.domain.vo.RouteVersionVo;
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
    private final ConfigAuditLogDao configAuditLogDao;

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
                entity.getConfigRevision(),
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
            String alias = request.routeSpec().alias();
            if (alias != null && !alias.isBlank() && !alias.equals(entity.getAlias())) {
                // 草稿规格可改名 alias：与 create 同口径重查唯一性（排除自身）并同步路由列；
                // 否则两条同 alias 路由发布后，Runtime 缓存后写覆盖先写，选路不确定
                boolean taken = routeDao.selectCount(new LambdaQueryWrapper<RouteEntity>()
                        .eq(RouteEntity::getAlias, alias)
                        .ne(RouteEntity::getRouteId, routeId)) > 0;
                if (taken) {
                    throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "route alias exists");
                }
                entity.setAlias(alias);
            }
            entity.setRouteSpec(NexmuxJson.write(request.routeSpec()));
        }
        routeDao.updateById(entity);
        return toVo(entity);
    }

    /**
     * 启用：仅 DISABLED → PUBLISHED；草稿须先发布。
     * 前置校验（D-01）：已发布规格须含 ≥1 条启用态候选模型。
     */
    public RouteVo enable(String routeId) {
        RouteEntity entity = require(routeId);
        if (!STATUS_DISABLED.equals(entity.getStatus())) {
            throw NexusException.build(
                    NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "only a disabled route can be enabled; publish drafts instead");
        }
        requireEnabledCandidate(entity.getPublishedRouteSpec());
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
        // D-01：发布即生效 Runtime 选路，候选模型 ≥1 且至少 1 条启用态方可发布
        requireEnabledCandidate(entity.getRouteSpec());
        String before = entity.getPublishedRouteSpec() == null ? "null" : entity.getPublishedRouteSpec();
        entity.setPublishedRouteSpec(entity.getRouteSpec());
        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(request.operatorId());
        entity.setConfigRevision(entity.getConfigRevision() == null ? 1 : entity.getConfigRevision() + 1);
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

    /** 查询发布历史，当前版本在前。 */
    public List<RouteVersionVo> versions(String routeCode) {
        RouteEntity entity = requireByCode(routeCode);
        List<ConfigAuditLogEntity> history = publishHistory(entity.getRouteId());
        long current = entity.getConfigRevision() == null ? history.size() : entity.getConfigRevision();
        if (history.isEmpty()) {
            return List.of(new RouteVersionVo(current, entity.getPublishedAt(),
                    entity.getPublishedBy(), "当前运行版本", true));
        }
        List<RouteVersionVo> versions = new java.util.ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            ConfigAuditLogEntity audit = history.get(i);
            versions.add(new RouteVersionVo(current - i, audit.getCreatedAt(), audit.getOperatorId(),
                    audit.getChangeSummary(), i == 0));
        }
        return List.copyOf(versions);
    }

    /** 回滚到历史发布规格，并作为新修订记录。 */
    public RouteVo rollback(String routeCode, Long targetRevision, String operatorId) {
        RouteEntity entity = requireByCode(routeCode);
        List<ConfigAuditLogEntity> history = publishHistory(entity.getRouteId());
        long current = entity.getConfigRevision() == null ? history.size() : entity.getConfigRevision();
        long target = targetRevision == null ? current - 1 : targetRevision;
        int index = (int) (current - target);
        if (target <= 0 || target >= current || index < 0 || index >= history.size()) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "route revision is not available for rollback");
        }
        var diff = NexmuxJson.read(history.get(index).getChangeDiff(),
                com.fasterxml.jackson.databind.JsonNode.class);
        String restored = diff == null || diff.get("after") == null ? null : diff.get("after").toString();
        if (restored == null || restored.isBlank()) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "route revision has no published spec");
        }
        String before = entity.getPublishedRouteSpec();
        entity.setRouteSpec(restored);
        entity.setPublishedRouteSpec(restored);
        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(operatorId);
        entity.setConfigRevision(current + 1);
        routeDao.updateById(entity);
        configAuditService.record("ROUTE", entity.getRouteId(), "ROLLBACK", "回滚运行配置",
                "{\"before\":" + before + ",\"after\":" + restored + "}", operatorId);
        return toVo(entity);
    }

    private List<ConfigAuditLogEntity> publishHistory(String routeId) {
        return configAuditLogDao.selectList(new LambdaQueryWrapper<ConfigAuditLogEntity>()
                .eq(ConfigAuditLogEntity::getResourceType, "ROUTE")
                .eq(ConfigAuditLogEntity::getResourceId, routeId)
                .eq(ConfigAuditLogEntity::getOperation, "PUBLISH")
                .orderByDesc(ConfigAuditLogEntity::getCreatedAt));
    }

    private RouteEntity requireByCode(String routeCode) {
        RouteEntity entity = routeDao.selectOne(new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getRouteCode, routeCode));
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "route not found");
        }
        return entity;
    }

    /**
     * 发布/启用前置校验（D-01）：候选模型 ≥1 且至少 1 条为启用态
     * （与 Runtime 选路口径一致：{@code enabled} 为 null 视为启用）。
     */
    private static void requireEnabledCandidate(String specJson) {
        RouteSpec spec = specJson == null || specJson.isBlank()
                ? null : NexmuxJson.read(specJson, RouteSpec.class);
        long enabled = spec == null ? 0 : spec.candidates().stream()
                .filter(c -> !Boolean.FALSE.equals(c.enabled()))
                .count();
        if (enabled == 0) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "请先添加至少 1 个启用状态的候选模型，再启用/发布路由");
        }
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
