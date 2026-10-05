package com.innospots.nexmux.core.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogDetailVo;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuditQueryService {

    private final ConfigAuditLogDao configAuditLogDao;

    public PageResult<AuditLogVo> page(AuditLogPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = buildWrapper(request);
        long total = configAuditLogDao.selectCount(wrapper);
        var records = configAuditLogDao
                .selectList(wrapper.orderByDesc(ConfigAuditLogEntity::getCreatedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public AuditLogDetailVo get(String auditId) {
        ConfigAuditLogEntity entity = configAuditLogDao.selectById(auditId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "audit log not found");
        }
        return new AuditLogDetailVo(
                entity.getAuditId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getOperation(),
                entity.getChangeSummary(),
                entity.getChangeDiff(),
                entity.getOperatorId(),
                entity.getClientIp(),
                entity.getRequestId(),
                entity.getUserAgent(),
                entity.getCreatedAt());
    }

    /**
     * 操作人去重列表（审计筛选下拉）。
     */
    public List<String> operators() {
        return configAuditLogDao.selectList(new LambdaQueryWrapper<ConfigAuditLogEntity>()
                        .isNotNull(ConfigAuditLogEntity::getOperatorId))
                .stream()
                .map(ConfigAuditLogEntity::getOperatorId)
                .distinct()
                .sorted()
                .toList();
    }

    private LambdaQueryWrapper<ConfigAuditLogEntity> buildWrapper(AuditLogPageRequest request) {
        var wrapper = new LambdaQueryWrapper<ConfigAuditLogEntity>();
        if (request.start() != null && !request.start().isBlank()) {
            wrapper.ge(ConfigAuditLogEntity::getCreatedAt,
                    LocalDate.parse(request.start().trim()).atStartOfDay());
        }
        if (request.end() != null && !request.end().isBlank()) {
            wrapper.le(ConfigAuditLogEntity::getCreatedAt,
                    LocalDate.parse(request.end().trim()).atTime(LocalTime.MAX));
        }
        if (request.operator() != null && !request.operator().isBlank()) {
            wrapper.eq(ConfigAuditLogEntity::getOperatorId, request.operator());
        }
        if (request.operation() != null && !request.operation().isBlank()) {
            wrapper.eq(ConfigAuditLogEntity::getOperation, request.operation());
        }
        if (request.resourceType() != null && !request.resourceType().isBlank()) {
            wrapper.eq(ConfigAuditLogEntity::getResourceType, request.resourceType());
        }
        if (request.resourceId() != null && !request.resourceId().isBlank()) {
            wrapper.like(ConfigAuditLogEntity::getResourceId, request.resourceId().trim());
        }
        return wrapper;
    }

    private AuditLogVo toVo(ConfigAuditLogEntity entity) {
        return new AuditLogVo(
                entity.getAuditId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getOperation(),
                entity.getChangeSummary(),
                entity.getChangeDiff(),
                entity.getOperatorId(),
                entity.getClientIp(),
                entity.getRequestId(),
                entity.getUserAgent(),
                entity.getCreatedAt());
    }
}
