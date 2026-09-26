package com.innospots.nexmux.core.access.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientRouteGrantsRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientCreateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientRotateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.security.AppKeySupport;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClientService {

    private final ClientDao clientDao;

    public PageResult<ClientVo> page(ClientPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ClientEntity>().orderByDesc(ClientEntity::getCreatedAt);
        long total = clientDao.selectCount(wrapper);
        var records = clientDao.selectList(wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public ClientVo get(String clientId) {
        return toVo(require(clientId));
    }

    public ClientCreateResultVo create(ClientCreateRequest request) {
        if (clientDao.selectCount(new LambdaQueryWrapper<ClientEntity>()
                .eq(ClientEntity::getClientCode, request.clientCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "client code exists");
        }
        AppKeySupport.Material material = AppKeySupport.generate();
        ClientEntity entity = new ClientEntity();
        entity.setClientCode(request.clientCode());
        entity.setDisplayName(request.displayName());
        entity.setStatus("ENABLED");
        entity.setAppKeyHash(material.hash());
        entity.setAppKeySalt(material.salt());
        entity.setAppKeyVersion(1);
        entity.setAuthorizedRouteIds(NexmuxJson.write(List.of()));
        clientDao.insert(entity);
        return new ClientCreateResultVo(toVo(entity), material.plainKey());
    }

    public ClientVo update(String clientId, ClientUpdateRequest request) {
        ClientEntity entity = require(clientId);
        entity.setDisplayName(request.displayName());
        if (request.quotaSpec() != null) {
            entity.setQuotaSpec(request.quotaSpec());
        }
        if (request.rateLimitSpec() != null) {
            entity.setRateLimitSpec(request.rateLimitSpec());
        }
        clientDao.updateById(entity);
        return toVo(entity);
    }

    public ClientRotateResultVo rotateAppKey(String clientId) {
        ClientEntity entity = require(clientId);
        AppKeySupport.Material material = AppKeySupport.generate();
        entity.setAppKeyHash(material.hash());
        entity.setAppKeySalt(material.salt());
        entity.setAppKeyVersion(entity.getAppKeyVersion() + 1);
        clientDao.updateById(entity);
        return new ClientRotateResultVo(entity.getClientId(), material.plainKey(), entity.getAppKeyVersion());
    }

    public ClientVo updateRouteGrants(String clientId, ClientRouteGrantsRequest request) {
        ClientEntity entity = require(clientId);
        entity.setAuthorizedRouteIds(NexmuxJson.write(request.routeIds()));
        clientDao.updateById(entity);
        return toVo(entity);
    }

    private ClientEntity require(String clientId) {
        ClientEntity entity = clientDao.selectById(clientId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client not found");
        }
        return entity;
    }

    private ClientVo toVo(ClientEntity entity) {
        return new ClientVo(
                entity.getClientId(),
                entity.getClientCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                NexmuxJson.readStringList(entity.getAuthorizedRouteIds()),
                entity.getQuotaSpec(),
                entity.getRateLimitSpec(),
                entity.getAppKeyVersion());
    }
}
