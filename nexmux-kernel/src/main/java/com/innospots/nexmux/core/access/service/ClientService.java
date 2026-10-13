package com.innospots.nexmux.core.access.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.access.dao.ClientDao;
import com.innospots.nexmux.core.access.dao.ClientTemplateDao;
import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.access.domain.entity.ClientTemplateEntity;
import com.innospots.nexmux.core.audit.service.ConfigAuditService;
import com.innospots.nexmux.core.access.domain.request.ClientCreateRequest;
import com.innospots.nexmux.core.access.domain.request.ClientPageRequest;
import com.innospots.nexmux.core.access.domain.request.ClientRouteGrantsRequest;
import com.innospots.nexmux.core.access.domain.request.ClientUpdateRequest;
import com.innospots.nexmux.core.access.domain.vo.ClientCreateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientDetailVo;
import com.innospots.nexmux.core.access.domain.vo.ClientRotateResultVo;
import com.innospots.nexmux.core.access.domain.vo.ClientVo;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.security.AppKeySupport;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClientService {

    private final ClientDao clientDao;
    private final ClientTemplateDao clientTemplateDao;
    private final ConfigAuditService configAuditService;

    public PageResult<ClientVo> page(ClientPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ClientEntity>();
        if (request.status() != null && !request.status().isBlank()) {
            wrapper.eq(ClientEntity::getStatus, request.status());
        }
        if (request.name() != null && !request.name().isBlank()) {
            wrapper.like(ClientEntity::getDisplayName, request.name().trim());
        }
        if (request.code() != null && !request.code().isBlank()) {
            wrapper.like(ClientEntity::getClientCode, request.code().trim());
        }
        // tags 在 CLOB JSON 内：内存过滤 + 内存分页（同 Route 域约定）
        List<ClientEntity> matched = clientDao.selectList(wrapper.orderByDesc(ClientEntity::getCreatedAt))
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
    public List<ClientVo> listAll() {
        return clientDao.selectList(new LambdaQueryWrapper<ClientEntity>()
                        .orderByAsc(ClientEntity::getCreatedAt))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public ClientVo get(String clientId) {
        return toVo(require(clientId));
    }

    /**
     * 按稳定业务键查询详情。
     */
    public ClientDetailVo getByCode(String clientCode) {
        ClientEntity entity = clientDao.selectOne(new LambdaQueryWrapper<ClientEntity>()
                .eq(ClientEntity::getClientCode, clientCode));
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client not found: " + clientCode);
        }
        return new ClientDetailVo(
                entity.getClientId(),
                entity.getClientCode(),
                entity.getDisplayName(),
                entity.getStatus(),
                appKeyIdOf(entity),
                entity.getAppKeyVersion(),
                entity.getAppKeyRotatedAt(),
                NexmuxJson.readStringList(entity.getAuthorizedRouteIds()),
                entity.getQuotaSpec(),
                entity.getRateLimitSpec(),
                entity.getSourceTemplateId(),
                NexmuxJson.readStringList(entity.getTags()),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
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
        entity.setAppKeyRotatedAt(LocalDateTime.now());
        entity.setTags(request.tags() == null ? null : NexmuxJson.write(request.tags()));
        applyTemplateOrBlank(entity, request);
        clientDao.insert(entity);
        configAuditService.record("CLIENT", entity.getClientId(), "CREATE",
                "创建客户端 " + entity.getClientCode(), null);
        return new ClientCreateResultVo(toVo(entity), material.plainKey());
    }

    /**
     * 模板创建：复制模板策略并按有效期规则计算到期时间；空白创建：授权与到期取请求。
     */
    private void applyTemplateOrBlank(ClientEntity entity, ClientCreateRequest request) {
        if (request.sourceTemplateCode() == null || request.sourceTemplateCode().isBlank()) {
            entity.setAuthorizedRouteIds(NexmuxJson.write(
                    request.routeIds() == null ? List.of() : request.routeIds()));
            entity.setQuotaSpec(request.quotaSpec());
            entity.setRateLimitSpec(request.rateLimitSpec());
            if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
                entity.setExpiresAt(parseExpiry(request.expiresAt()));
            }
            return;
        }
        ClientTemplateEntity template = clientTemplateDao.selectOne(
                new LambdaQueryWrapper<ClientTemplateEntity>()
                        .eq(ClientTemplateEntity::getTemplateCode, request.sourceTemplateCode()));
        if (template == null) {
            throw NexusException.build(
                    NexmuxStatusCode.RESOURCE_NOT_FOUND,
                    "client template not found: " + request.sourceTemplateCode());
        }
        entity.setSourceTemplateId(template.getTemplateId());
        entity.setAuthorizedRouteIds(template.getAuthorizedRouteIds());
        entity.setQuotaSpec(template.getQuotaSpec());
        entity.setRateLimitSpec(template.getRateLimitSpec());
        entity.setExpiresAt(computeExpiry(template));
    }

    static LocalDateTime computeExpiry(ClientTemplateEntity template) {
        if ("days".equals(template.getValidityType()) && template.getValidityDays() != null) {
            return LocalDateTime.now().plusDays(template.getValidityDays());
        }
        if ("fixed".equals(template.getValidityType())) {
            return template.getValidTo();
        }
        return null; // never 永不过期
    }

    public ClientVo update(String clientId, ClientUpdateRequest request) {
        ClientEntity entity = require(clientId);
        if (request.displayName() != null) {
            entity.setDisplayName(request.displayName());
        }
        if (request.quotaSpec() != null) {
            entity.setQuotaSpec(request.quotaSpec());
        }
        if (request.rateLimitSpec() != null) {
            entity.setRateLimitSpec(request.rateLimitSpec());
        }
        if (request.tags() != null) {
            entity.setTags(NexmuxJson.write(request.tags()));
        }
        clientDao.updateById(entity);
        configAuditService.record("CLIENT", entity.getClientId(), "UPDATE",
                "更新客户端 " + entity.getClientCode(), null);
        return toVo(entity);
    }

    public ClientVo setEnabled(String clientId, boolean enabled) {
        ClientEntity entity = require(clientId);
        entity.setStatus(enabled ? "ENABLED" : "DISABLED");
        clientDao.updateById(entity);
        configAuditService.record("CLIENT", entity.getClientId(),
                enabled ? "ENABLE" : "DISABLE",
                (enabled ? "启用" : "停用") + "客户端 " + entity.getClientCode(), null);
        return toVo(entity);
    }

    public ClientVo disable(String clientId) {
        return setEnabled(clientId, false);
    }

    public void delete(String clientId) {
        ClientEntity entity = require(clientId);
        clientDao.deleteById(clientId);
        configAuditService.record("CLIENT", entity.getClientId(), "DELETE",
                "删除客户端 " + entity.getClientCode(), null);
    }

    public ClientRotateResultVo rotateAppKey(String clientId) {
        ClientEntity entity = require(clientId);
        AppKeySupport.Material material = AppKeySupport.generate();
        entity.setAppKeyHash(material.hash());
        entity.setAppKeySalt(material.salt());
        entity.setAppKeyVersion(entity.getAppKeyVersion() + 1);
        entity.setAppKeyRotatedAt(LocalDateTime.now());
        clientDao.updateById(entity);
        configAuditService.record("CLIENT", entity.getClientId(), "ROTATE",
                "轮换 AppKey（v" + entity.getAppKeyVersion() + "）", null);
        return new ClientRotateResultVo(entity.getClientId(), material.plainKey(), entity.getAppKeyVersion());
    }

    public ClientVo updateRouteGrants(String clientId, ClientRouteGrantsRequest request) {
        ClientEntity entity = require(clientId);
        entity.setAuthorizedRouteIds(NexmuxJson.write(request.routeIds()));
        clientDao.updateById(entity);
        configAuditService.record("CLIENT", entity.getClientId(), "UPDATE",
                "更新路由授权（" + request.routeIds().size() + " 条）", null);
        return toVo(entity);
    }

    /** 到期时间解析：日期（yyyy-MM-dd）视为当天 23:59:59；带时间按 ISO / 空格分隔解析。 */
    private static LocalDateTime parseExpiry(String raw) {
        String trimmed = raw.trim();
        // 纯日期取当日 23:59:59（秒精度）：H2 TIMESTAMP 默认微秒精度会把
        // LocalTime.MAX（999999999 纳秒）四舍五入进位到次日 00:00（D-03）
        return trimmed.length() == 10
                ? LocalDate.parse(trimmed).atTime(23, 59, 59)
                : LocalDateTime.parse(trimmed.replace(" ", "T"));
    }

    private boolean matchesTags(ClientEntity entity, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        List<String> own = NexmuxJson.readStringList(entity.getTags());
        return tags.stream().allMatch(own::contains);
    }

    private ClientEntity require(String clientId) {
        ClientEntity entity = clientDao.selectById(clientId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "client not found");
        }
        return entity;
    }

    /** AppKey 展示 ID：gw-ak- + 哈希前 8 位（不含可逆信息）。 */
    public static String appKeyIdOf(ClientEntity entity) {
        String hash = entity.getAppKeyHash();
        return "gw-ak-" + (hash == null ? "--------" : hash.substring(0, Math.min(8, hash.length())));
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
                entity.getAppKeyVersion(),
                appKeyIdOf(entity),
                entity.getTags(),
                entity.getSourceTemplateId(),
                entity.getExpiresAt(),
                entity.getAppKeyRotatedAt());
    }
}
