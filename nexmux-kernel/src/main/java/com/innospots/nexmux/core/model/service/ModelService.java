package com.innospots.nexmux.core.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.model.dao.ModelDao;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.model.domain.request.ModelCreateRequest;
import com.innospots.nexmux.core.model.domain.request.ModelPageRequest;
import com.innospots.nexmux.core.model.domain.vo.ModelVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ModelService {

    private final ModelDao modelDao;

    public PageResult<ModelVo> page(ModelPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ModelEntity>().orderByDesc(ModelEntity::getCreatedAt);
        long total = modelDao.selectCount(wrapper);
        var records = modelDao.selectList(wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public ModelVo get(String modelId) {
        return toVo(require(modelId));
    }

    public ModelVo create(ModelCreateRequest request) {
        if (modelDao.selectCount(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getModelCode, request.modelCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "model code exists");
        }
        ModelEntity entity = new ModelEntity();
        entity.setModelCode(request.modelCode());
        entity.setDisplayName(request.displayName());
        entity.setModelType(request.modelType());
        entity.setStatus("DRAFT");
        entity.setPrimaryBindingEnabled(true);
        entity.setExtraBindings("[]");
        entity.setPrimaryCostMultiplier(BigDecimal.ONE);
        entity.setPrimaryReferenceMultiplier(BigDecimal.ONE);
        entity.setPrimaryBindingWeight(100);
        modelDao.insert(entity);
        return toVo(entity);
    }

    public ModelVo setEnabled(String modelId, boolean enabled) {
        ModelEntity entity = require(modelId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        modelDao.updateById(entity);
        return toVo(entity);
    }

    public boolean invokeTest(String modelId) {
        require(modelId);
        return true;
    }

    private ModelEntity require(String modelId) {
        ModelEntity entity = modelDao.selectById(modelId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "model not found");
        }
        return entity;
    }

    private ModelVo toVo(ModelEntity entity) {
        return new ModelVo(entity.getModelId(), entity.getModelCode(), entity.getDisplayName(), entity.getStatus());
    }
}
