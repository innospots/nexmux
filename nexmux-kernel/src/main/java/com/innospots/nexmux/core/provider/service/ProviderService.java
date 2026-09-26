package com.innospots.nexmux.core.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.provider.dao.ProviderCredentialDao;
import com.innospots.nexmux.core.provider.dao.ProviderDao;
import com.innospots.nexmux.core.provider.domain.entity.ProviderCredentialEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.provider.domain.request.ProviderCreateRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderPageRequest;
import com.innospots.nexmux.core.provider.domain.request.ProviderUpdateRequest;
import com.innospots.nexmux.core.provider.domain.vo.ProviderVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProviderService {

    private final ProviderDao providerDao;
    private final ProviderCredentialDao providerCredentialDao;

    public PageResult<ProviderVo> page(ProviderPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ProviderEntity>().orderByDesc(ProviderEntity::getCreatedAt);
        long total = providerDao.selectCount(wrapper);
        var records = providerDao.selectList(wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public ProviderVo get(String providerId) {
        return toVo(require(providerId));
    }

    public ProviderVo create(ProviderCreateRequest request) {
        if (providerDao.selectCount(new LambdaQueryWrapper<ProviderEntity>()
                .eq(ProviderEntity::getProviderCode, request.providerCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "provider code exists");
        }
        ProviderEntity entity = new ProviderEntity();
        entity.setProviderCode(request.providerCode());
        entity.setDisplayName(request.displayName());
        entity.setProviderType(request.providerType());
        entity.setDefaultProtocol(request.defaultProtocol());
        entity.setVendorCode(request.vendorCode());
        entity.setSourceVendorCode(
                request.sourceVendorCode() == null ? request.vendorCode() : request.sourceVendorCode());
        entity.setIconUri(request.iconUri());
        entity.setBaseUrl(request.baseUrl());
        entity.setRegion(request.region());
        entity.setTimeoutMs(request.timeoutMs() == null ? 60000 : request.timeoutMs());
        entity.setStatus("ENABLED");
        providerDao.insert(entity);
        return toVo(entity);
    }

    public ProviderVo update(String providerId, ProviderUpdateRequest request) {
        ProviderEntity entity = require(providerId);
        entity.setDisplayName(request.displayName());
        entity.setBaseUrl(request.baseUrl());
        entity.setRegion(request.region());
        if (request.timeoutMs() != null) {
            entity.setTimeoutMs(request.timeoutMs());
        }
        providerDao.updateById(entity);
        return toVo(entity);
    }

    public ProviderVo setEnabled(String providerId, boolean enabled) {
        ProviderEntity entity = require(providerId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        providerDao.updateById(entity);
        return toVo(entity);
    }

    public boolean connectionTest(String providerId) {
        require(providerId);
        return true;
    }

    private ProviderEntity require(String providerId) {
        ProviderEntity entity = providerDao.selectById(providerId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "provider not found");
        }
        return entity;
    }

    private ProviderVo toVo(ProviderEntity entity) {
        long credCount = providerCredentialDao.selectCount(new LambdaQueryWrapper<ProviderCredentialEntity>()
                .eq(ProviderCredentialEntity::getProviderId, entity.getProviderId()));
        return new ProviderVo(
                entity.getProviderId(),
                entity.getProviderCode(),
                entity.getDisplayName(),
                entity.getProviderType(),
                entity.getDefaultProtocol(),
                entity.getVendorCode(),
                entity.getSourceVendorCode(),
                entity.getIconUri(),
                entity.getBaseUrl(),
                entity.getRegion(),
                entity.getTimeoutMs(),
                entity.getStatus(),
                credCount > 0);
    }
}
