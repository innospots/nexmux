package com.innospots.nexmux.core.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.request.ModelUpdateRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelDetailVo;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.service.ProviderService;
import com.innospots.nexmux.core.debug.service.DebugService;
import com.innospots.nexmux.core.debug.domain.request.ModelInvokeDebugRequest;
import com.innospots.nexmux.core.debug.domain.vo.DebugInvokeResultVo;
import com.innospots.nexmux.core.route.dao.RouteDao;
import com.innospots.nexmux.core.route.domain.entity.RouteEntity;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.domain.response.PageResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ModelService {

    private final ModelDao modelDao;
    private final ProviderDao providerDao;
    private final RouteDao routeDao;
    private final ConfigAuditService configAuditService;
    private final ProviderService providerService;
    private final DebugService debugService;

    public PageResult<ModelVo> page(ModelPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = buildPageWrapper(request);
        long total = modelDao.selectCount(wrapper);
        var records = modelDao.selectList(wrapper.orderByDesc(ModelEntity::getCreatedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    /**
     * 全量查询（Console 列表页构成口径与关联统计用；P0 数据量小，不分页）。
     */
    public List<ModelVo> listAll() {
        return modelDao.selectList(new LambdaQueryWrapper<ModelEntity>()
                        .orderByAsc(ModelEntity::getCreatedAt))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public ModelVo get(String modelId) {
        return toVo(require(modelId));
    }

    /**
     * 按稳定业务键查询详情（绑定 / 定价 / 默认参数以 JSON 原文透传）。
     */
    public ModelDetailVo getByCode(String modelCode) {
        ModelEntity entity = modelDao.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getModelCode, modelCode));
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "model not found: " + modelCode);
        }
        return new ModelDetailVo(
                entity.getModelId(),
                entity.getModelCode(),
                entity.getDisplayName(),
                entity.getModelType(),
                entity.getStatus(),
                entity.getIconUri(),
                entity.getReleaseDate(),
                entity.getContextLength(),
                entity.getMaxOutputTokens(),
                entity.getInputModalities(),
                entity.getOutputModalities(),
                entity.getCapabilityTags(),
                entity.getTags(),
                entity.getDescription(),
                entity.getMetadataSpec(),
                entity.getDefaultParams(),
                entity.getPrimaryProviderId(),
                entity.getPrimaryProviderModelId(),
                entity.getPrimaryUpstreamProtocol(),
                entity.getPrimaryBindingWeight(),
                entity.getPrimaryCostMultiplier(),
                entity.getPrimaryReferenceMultiplier(),
                entity.getPrimaryBindingEnabled(),
                entity.getExtraBindings(),
                entity.getPricingSpec(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public ModelVo create(ModelCreateRequest request) {
        if (modelDao.selectCount(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getModelCode, request.modelCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "model code exists");
        }
        ProviderEntity primaryProvider = providerDao.selectOne(new LambdaQueryWrapper<ProviderEntity>()
                .eq(ProviderEntity::getProviderCode, request.primaryProviderCode()));
        if (primaryProvider == null) {
            throw NexusException.build(
                    NexmuxStatusCode.RESOURCE_NOT_FOUND,
                    "provider not found: " + request.primaryProviderCode());
        }
        ModelEntity entity = new ModelEntity();
        entity.setModelCode(request.modelCode());
        entity.setDisplayName(request.displayName());
        entity.setModelType(request.modelType());
        entity.setStatus("DRAFT");
        entity.setContextLength(request.contextLength());
        entity.setMaxOutputTokens(request.maxOutputTokens());
        entity.setInputModalities(joinCsv(request.inputModalities()));
        entity.setOutputModalities(joinCsv(request.outputModalities()));
        entity.setTags(request.tags() == null ? null : NexmuxJson.write(request.tags()));
        entity.setDescription(request.description());
        entity.setMetadataSpec(request.metadataSpec());
        entity.setDefaultParams(request.defaultParams());
        entity.setCapabilityTags(request.capabilityTags());
        entity.setPrimaryProviderId(primaryProvider.getProviderId());
        entity.setPrimaryProviderModelId(request.primaryProviderModelId());
        entity.setPrimaryUpstreamProtocol(request.primaryUpstreamProtocol());
        entity.setPrimaryBindingEnabled(request.primaryBindingEnabled() == null
                || request.primaryBindingEnabled());
        entity.setExtraBindings(request.extraBindings() == null ? "[]" : request.extraBindings());
        entity.setPrimaryCostMultiplier(request.primaryCostMultiplier() == null
                ? BigDecimal.ONE : request.primaryCostMultiplier());
        entity.setPrimaryReferenceMultiplier(request.primaryReferenceMultiplier() == null
                ? BigDecimal.ONE : request.primaryReferenceMultiplier());
        entity.setPrimaryBindingWeight(request.primaryBindingWeight() == null
                ? 100 : request.primaryBindingWeight());
        entity.setPricingSpec(initialPricing(request.pricingSpec()));
        modelDao.insert(entity);
        configAuditService.record("MODEL", entity.getModelId(), "CREATE",
                "创建模型 " + entity.getModelCode(), null);
        return toVo(entity);
    }

    public ModelVo update(String modelId, ModelUpdateRequest request) {
        ModelEntity entity = require(modelId);
        if (request.displayName() != null) {
            entity.setDisplayName(request.displayName());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.contextLength() != null) {
            entity.setContextLength(request.contextLength());
        }
        if (request.maxOutputTokens() != null) {
            entity.setMaxOutputTokens(request.maxOutputTokens());
        }
        if (request.inputModalities() != null) {
            entity.setInputModalities(joinCsv(request.inputModalities()));
        }
        if (request.outputModalities() != null) {
            entity.setOutputModalities(joinCsv(request.outputModalities()));
        }
        if (request.tags() != null) {
            entity.setTags(NexmuxJson.write(request.tags()));
        }
        if (request.metadataSpec() != null) {
            entity.setMetadataSpec(request.metadataSpec());
        }
        if (request.defaultParams() != null) {
            entity.setDefaultParams(request.defaultParams());
        }
        if (request.capabilityTags() != null) {
            entity.setCapabilityTags(request.capabilityTags());
        }
        if (request.primaryBindingWeight() != null) {
            entity.setPrimaryBindingWeight(request.primaryBindingWeight());
        }
        if (request.primaryCostMultiplier() != null) {
            entity.setPrimaryCostMultiplier(request.primaryCostMultiplier());
        }
        if (request.primaryReferenceMultiplier() != null) {
            entity.setPrimaryReferenceMultiplier(request.primaryReferenceMultiplier());
        }
        if (request.primaryBindingEnabled() != null) {
            entity.setPrimaryBindingEnabled(request.primaryBindingEnabled());
        }
        if (request.extraBindings() != null) {
            entity.setExtraBindings(request.extraBindings());
        }
        if (request.pricingSpec() != null
                && !request.pricingSpec().equals(entity.getPricingSpec())) {
            entity.setPricingSpec(versionedPricing(entity.getPricingSpec(), request.pricingSpec()));
        }
        modelDao.updateById(entity);
        configAuditService.record("MODEL", entity.getModelId(), "UPDATE",
                "更新模型 " + entity.getModelCode(), null);
        return toVo(entity);
    }

    public ModelVo setEnabled(String modelId, boolean enabled) {
        ModelEntity entity = require(modelId);
        if (enabled) {
            requireReady(entity);
        }
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        modelDao.updateById(entity);
        configAuditService.record("MODEL", entity.getModelId(),
                enabled ? "ENABLE" : "DISABLE",
                (enabled ? "启用" : "停用") + "模型 " + entity.getModelCode(), null);
        return toVo(entity);
    }

    public ModelVo disable(String modelId) {
        return setEnabled(modelId, false);
    }

    public void delete(String modelId) {
        ModelEntity entity = require(modelId);
        requireNoRouteReference(entity.getModelCode());
        modelDao.deleteById(modelId);
        configAuditService.record("MODEL", entity.getModelId(), "DELETE",
                "删除模型 " + entity.getModelCode(), null);
    }

    public boolean invokeTest(String modelId) {
        ModelEntity entity = require(modelId);
        requireReady(entity);
        DebugInvokeResultVo result = debugService.modelInvoke(
                new ModelInvokeDebugRequest(entity.getModelCode(), null,
                        "Nexmux model invocation test", Map.of("max_tokens", 1)));
        return result != null && result.status() >= 200 && result.status() < 300;
    }

    /** 启用与调用测试共用前置检查，避免无效模型被伪装为可用。 */
    private void requireReady(ModelEntity entity) {
        ProviderEntity provider = entity.getPrimaryProviderId() == null
                ? null : providerDao.selectById(entity.getPrimaryProviderId());
        if (provider == null || !"ENABLED".equals(provider.getStatus())
                || !Boolean.TRUE.equals(entity.getPrimaryBindingEnabled())
                || entity.getPrimaryProviderModelId() == null
                || entity.getPrimaryProviderModelId().isBlank()) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "模型启用前检查失败：Provider 或 Provider Model Binding 不可用");
        }
        var connection = providerService.connectionTest(provider.getProviderId());
        if (connection == null || !connection.ok()) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                    "模型启用前检查失败：Provider 连接测试未通过");
        }
    }

    private void requireNoRouteReference(String modelCode) {
        try {
            boolean referenced = routeDao.selectList(null).stream()
                    .anyMatch(route -> referencesModel(route, modelCode));
            if (referenced) {
                throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED,
                        "该模型仍被路由引用，请先解除引用后再删除");
            }
        } catch (IllegalArgumentException error) {
            throw NexusException.build(NexmuxStatusCode.OPERATION_PRECONDITION_FAILED, error);
        }
    }

    private static boolean referencesModel(RouteEntity route, String modelCode) {
        return referencesModel(route.getRouteSpec(), modelCode)
                || referencesModel(route.getPublishedRouteSpec(), modelCode);
    }

    private static boolean referencesModel(String specJson, String modelCode) {
        if (specJson == null || specJson.isBlank()) {
            return false;
        }
        RouteSpec spec = NexmuxJson.read(specJson, RouteSpec.class);
        return spec != null && spec.candidates().stream()
                .anyMatch(candidate -> modelCode.equals(candidate.modelCode()));
    }

    private LambdaQueryWrapper<ModelEntity> buildPageWrapper(ModelPageRequest request) {
        var wrapper = new LambdaQueryWrapper<ModelEntity>();
        if (request.type() != null && !request.type().isBlank()) {
            wrapper.eq(ModelEntity::getModelType, request.type());
        }
        if (request.keyword() != null && !request.keyword().isBlank()) {
            String keyword = request.keyword().trim();
            wrapper.and(w -> w.like(ModelEntity::getModelCode, keyword)
                    .or().like(ModelEntity::getDisplayName, keyword));
        }
        if (request.providers() != null && !request.providers().isEmpty()) {
            // 厂商编码 → providerId 集合（两次单表查询，避免 join）
            List<String> providerIds = providerDao.selectList(new LambdaQueryWrapper<ProviderEntity>()
                            .in(ProviderEntity::getProviderCode, request.providers()))
                    .stream()
                    .map(ProviderEntity::getProviderId)
                    .toList();
            if (providerIds.isEmpty()) {
                wrapper.eq(ModelEntity::getModelId, "__none__");
            } else {
                wrapper.in(ModelEntity::getPrimaryProviderId, providerIds);
            }
        }
        return wrapper;
    }

    private ModelEntity require(String modelId) {
        ModelEntity entity = modelDao.selectById(modelId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "model not found");
        }
        return entity;
    }

    private static String initialPricing(String pricingSpec) {
        if (pricingSpec == null || pricingSpec.isBlank()) {
            return pricingSpec;
        }
        ObjectNode pricing = NexmuxJson.read(pricingSpec, ObjectNode.class);
        String version = pricing.path("priceVersion").asText("v1");
        pricing.put("priceVersion", version);
        pricing.put("priceEffective", pricing.path("priceEffective").asText(LocalDateTime.now().toString()));
        if (!pricing.has("priceVersions")) {
            pricing.set("priceVersions", NexmuxJson.read("[]", ArrayNode.class));
        }
        ArrayNode history = (ArrayNode) pricing.get("priceVersions");
        if (history.isEmpty()) {
            history.add(priceHistoryEntry(pricing, version, true));
        }
        return NexmuxJson.write(pricing);
    }

    private static String versionedPricing(String previousSpec, String nextSpec) {
        ObjectNode previous = previousSpec == null || previousSpec.isBlank()
                ? null : NexmuxJson.read(previousSpec, ObjectNode.class);
        ObjectNode next = NexmuxJson.read(nextSpec, ObjectNode.class);
        String previousVersion = previous == null ? null : previous.path("priceVersion").asText(null);
        String nextVersion = next.path("priceVersion").asText(null);
        if (nextVersion == null || nextVersion.isBlank() || nextVersion.equals(previousVersion)) {
            nextVersion = "v-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        next.put("priceVersion", nextVersion);
        next.put("priceEffective", next.path("priceEffective").asText(LocalDateTime.now().toString()));
        if (!next.has("priceVersions")) {
            next.set("priceVersions", NexmuxJson.read("[]", ArrayNode.class));
        }
        ArrayNode history = (ArrayNode) next.get("priceVersions");
        history.forEach(entry -> ((ObjectNode) entry).put("current", false));
        if (previous != null) {
            history.add(priceHistoryEntry(previous,
                    previousVersion == null ? "v-legacy" : previousVersion, false));
        }
        history.add(priceHistoryEntry(next, nextVersion, true));
        return NexmuxJson.write(next);
    }

    private static ObjectNode priceHistoryEntry(ObjectNode pricing, String version, boolean current) {
        ObjectNode entry = pricing.deepCopy();
        entry.put("version", version);
        entry.put("current", current);
        entry.put("effective", pricing.path("priceEffective").asText(null));
        entry.put("summary", pricing.toString());
        return entry;
    }

    private static String joinCsv(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    private ModelVo toVo(ModelEntity entity) {
        return new ModelVo(
                entity.getModelId(),
                entity.getModelCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getModelType(),
                entity.getContextLength(),
                entity.getMaxOutputTokens(),
                entity.getPrimaryProviderId(),
                entity.getPricingSpec(),
                entity.getExtraBindings());
    }
}
