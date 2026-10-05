package com.innospots.nexmux.core.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import com.innospots.nexmux.core.audit.domain.request.AuditLogPageRequest;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuditQueryService {

    private final ConfigAuditLogDao configAuditLogDao;

    public PageResult<AuditLogVo> page(AuditLogPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<ConfigAuditLogEntity>();
        long total = configAuditLogDao.selectCount(wrapper);
        var records = configAuditLogDao
                .selectList(wrapper.orderByDesc(ConfigAuditLogEntity::getCreatedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public AuditLogVo get(String auditId) {
        ConfigAuditLogEntity entity = configAuditLogDao.selectById(auditId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "audit log not found");
        }
        return toVo(entity);
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
                entity.getCreatedAt());
    }
}
