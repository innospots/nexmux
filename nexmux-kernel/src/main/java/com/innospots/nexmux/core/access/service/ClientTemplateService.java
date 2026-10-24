package com.innospots.nexmux.core.access.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.dao.ClientTemplateDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.domain.entity.ClientTemplateEntity;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplatePageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientTemplateUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientTemplateVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClientTemplateService {

    private final ClientTemplateDao clientTemplateDao;
    private final ClientDao clientDao;
    private final ConfigAuditService configAuditService;

    public PageResult<ClientTemplateVo> page(ClientTemplatePageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ClientTemplateEntity>();
        if (request.status() != null && !request.status().isBlank()) {
            wrapper.eq(ClientTemplateEntity::getStatus, request.status());
        }
        if (request.keyword() != null && !request.keyword().isBlank()) {
            String keyword = request.keyword().trim();
            wrapper.and(w -> w.like(ClientTemplateEntity::getDisplayName, keyword)
                    .or().like(ClientTemplateEntity::getTemplateCode, keyword));
        }
        // tags 在 CLOB JSON 内：内存过滤 + 内存分页
        List<ClientTemplateEntity> matched = clientTemplateDao.selectList(
                        wrapper.orderByDesc(ClientTemplateEntity::getCreatedAt))
                .stream()
                .filter(e -> matchesTags(e, request.tags()))
                .toList();
        long from = Math.min((pageNo - 1) * pageSize, matched.size());
        long to = Math.min(from + pageSize, matched.size());
        var records = matched.subList((int) from, (int) to).stream().map(this::toVo).toList();
        return PageResult.of(records, pageNo, pageSize, matched.size());
    }

    /**
     * 全量查询（Console 列表页构成口径与关联统计用；P0 数据量小，不分页）。
     */
    public List<ClientTemplateVo> listAll() {
        return clientTemplateDao.selectList(new LambdaQueryWrapper<ClientTemplateEntity>()
                        .orderByAsc(ClientTemplateEntity::getCreatedAt))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public ClientTemplateDetailVo getByCode(String templateCode) {
        ClientTemplateEntity entity = clientTemplateDao.selectOne(
                new LambdaQueryWrapper<ClientTemplateEntity>()
                        .eq(ClientTemplateEntity::getTemplateCode, templateCode));
        if (entity == null) {
            throw NexusException.build(
                    NexmuxStatusCode.RESOURCE_NOT_FOUND, "client template not found: " + templateCode);
        }
        return new ClientTemplateDetailVo(
                entity.getTemplateId(),
                entity.getTemplateCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getDescription(),
                NexmuxJson.readStringList(entity.getTags()),
                entity.getValidityType(),
                entity.getValidityDays(),
                entity.getValidTo(),
                NexmuxJson.readStringList(entity.getAuthorizedRouteIds()),
                entity.getQuotaSpec(),
                entity.getRateLimitSpec(),
                countCreatedClients(entity.getTemplateId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public ClientTemplateVo create(ClientTemplateCreateRequest request) {
        if (clientTemplateDao.selectCount(new LambdaQueryWrapper<ClientTemplateEntity>()
                .eq(ClientTemplateEntity::getTemplateCode, request.templateCode())) > 0) {
            throw NexusException.build(NexmuxStatusCode.DUPLICATE_BUSINESS_KEY, "template code exists");
        }
        ClientTemplateEntity entity = new ClientTemplateEntity();
        entity.setTemplateCode(request.templateCode());
        entity.setDisplayName(request.displayName());
        entity.setDescription(request.description());
        entity.setStatus("ENABLED");
        entity.setTags(request.tags() == null ? null : NexmuxJson.write(request.tags()));
        entity.setAuthorizedRouteIds(NexmuxJson.write(
                request.routeIds() == null ? List.of() : request.routeIds()));
        entity.setQuotaSpec(request.quotaSpec());
        entity.setRateLimitSpec(request.rateLimitSpec());
        entity.setValidityType(request.validityType());
        entity.setValidityDays(request.validityDays());
        entity.setValidTo(parseFixedDate(request.validityFixedDate()));
        clientTemplateDao.insert(entity);
        configAuditService.record("CLIENT_TEMPLATE", entity.getTemplateId(), "CREATE",
                "创建客户端模板 " + entity.getTemplateCode(), null);
        return toVo(entity);
    }

    public ClientTemplateVo update(String templateId, ClientTemplateUpdateRequest request) {
        ClientTemplateEntity entity = require(templateId);
        if (request.displayName() != null) {
            entity.setDisplayName(request.displayName());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.tags() != null) {
            entity.setTags(NexmuxJson.write(request.tags()));
        }
        if (request.validityType() != null) {
            entity.setValidityType(request.validityType());
            entity.setValidityDays(request.validityDays());
            entity.setValidTo(parseFixedDate(request.validityFixedDate()));
        }
        if (request.routeIds() != null) {
            entity.setAuthorizedRouteIds(NexmuxJson.write(request.routeIds()));
        }
        if (request.quotaSpec() != null) {
            entity.setQuotaSpec(request.quotaSpec());
        }
        if (request.rateLimitSpec() != null) {
            entity.setRateLimitSpec(request.rateLimitSpec());
        }
        clientTemplateDao.updateById(entity);
        configAuditService.record("CLIENT_TEMPLATE", entity.getTemplateId(), "UPDATE",
                "更新客户端模板 " + entity.getTemplateCode(), null);
        return toVo(entity);
    }

    public ClientTemplateVo setEnabled(String templateId, boolean enabled) {
        ClientTemplateEntity entity = require(templateId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        clientTemplateDao.updateById(entity);
        configAuditService.record("CLIENT_TEMPLATE", entity.getTemplateId(),
                enabled ? "ENABLE" : "DISABLE",
                (enabled ? "启用" : "停用") + "客户端模板 " + entity.getTemplateCode(), null);
        return toVo(entity);
    }

    public ClientTemplateVo disable(String templateId) {
        return setEnabled(templateId, false);
    }

    public void delete(String templateId) {
        ClientTemplateEntity entity = require(templateId);
        clientTemplateDao.deleteById(templateId);
        configAuditService.record("CLIENT_TEMPLATE", entity.getTemplateId(), "DELETE",
                "删除客户端模板 " + entity.getTemplateCode(), null);
    }

    /**
     * 基于本模板创建的客户端列表。
     */
    public List<ClientVo> createdClients(String templateId) {
        require(templateId);
        return clientDao.selectList(new LambdaQueryWrapper<ClientEntity>()
                        .eq(ClientEntity::getSourceTemplateId, templateId)
                        .orderByDesc(ClientEntity::getCreatedAt))
                .stream()
                .map(this::toClientVo)
                .toList();
    }

    private long countCreatedClients(String templateId) {
        return clientDao.selectCount(new LambdaQueryWrapper<ClientEntity>()
                .eq(ClientEntity::getSourceTemplateId, templateId));
    }

    private boolean matchesTags(ClientTemplateEntity entity, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        List<String> own = NexmuxJson.readStringList(entity.getTags());
        return tags.stream().allMatch(own::contains);
    }

    private ClientTemplateEntity require(String templateId) {
        ClientTemplateEntity entity = clientTemplateDao.selectById(templateId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client template not found");
        }
        return entity;
    }

    private static LocalDateTime parseFixedDate(String fixedDate) {
        if (fixedDate == null || fixedDate.isBlank()) {
            return null;
        }
        // 当日 23:59:59（秒精度）：避免 H2 TIMESTAMP 微秒舍入把 LocalTime.MAX 进位到次日（D-03）
        return LocalDate.parse(fixedDate.trim()).atTime(23, 59, 59);
    }

    private ClientTemplateVo toVo(ClientTemplateEntity entity) {
        return new ClientTemplateVo(
                entity.getTemplateId(),
                entity.getTemplateCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getTags(),
                entity.getValidityType(),
                entity.getValidityDays(),
                entity.getValidTo(),
                NexmuxJson.readStringList(entity.getAuthorizedRouteIds()),
                entity.getQuotaSpec(),
                entity.getRateLimitSpec(),
                countCreatedClients(entity.getTemplateId()));
    }

    private ClientVo toClientVo(ClientEntity entity) {
        return new ClientVo(
                entity.getClientId(),
                entity.getClientCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                NexmuxJson.readStringList(entity.getAuthorizedRouteIds()),
                entity.getQuotaSpec(),
                entity.getRateLimitSpec(),
                entity.getAppKeyVersion(),
                ClientService.appKeyIdOf(entity),
                entity.getTags(),
                entity.getSourceTemplateId(),
                entity.getExpiresAt(),
                entity.getAppKeyRotatedAt());
    }
}
