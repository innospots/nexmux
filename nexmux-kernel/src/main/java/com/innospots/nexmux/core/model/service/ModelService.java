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
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ModelService {

    private final ModelDao modelDao;
    private final ProviderDao providerDao;

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
        entity.setPrimaryProviderId(primaryProvider.getProviderId());
        entity.setPrimaryProviderModelId(request.primaryProviderModelId());
        entity.setPrimaryUpstreamProtocol(request.primaryUpstreamProtocol());
        entity.setPrimaryBindingEnabled(true);
        entity.setExtraBindings("[]");
        entity.setPrimaryCostMultiplier(BigDecimal.ONE);
        entity.setPrimaryReferenceMultiplier(BigDecimal.ONE);
        entity.setPrimaryBindingWeight(100);
        modelDao.insert(entity);
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
        modelDao.updateById(entity);
        return toVo(entity);
    }

    public ModelVo setEnabled(String modelId, boolean enabled) {
        ModelEntity entity = require(modelId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        modelDao.updateById(entity);
        return toVo(entity);
    }

    public ModelVo disable(String modelId) {
        return setEnabled(modelId, false);
    }

    public boolean invokeTest(String modelId) {
        require(modelId);
        return true;
    }

    private LambdaQueryWrapper<ModelEntity> buildPageWrapper(ModelPageRequest request) {
        var wrapper = new LambdaQueryWrapper<ModelEntity>();
        if (request.type() != null && !request.type().isBlank()) {
            wrapper.eq(ModelEntity::getModelType, request.type());
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
