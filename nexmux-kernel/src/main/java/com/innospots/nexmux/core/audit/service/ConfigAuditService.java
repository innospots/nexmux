package com.innospots.nexmux.core.audit.service;

import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConfigAuditService {

    private final ConfigAuditLogDao configAuditLogDao;

    public void record(String resourceType, String resourceId, String operation, String summary, String changeDiff) {
        ConfigAuditLogEntity entity = new ConfigAuditLogEntity();
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setOperation(operation);
        entity.setChangeSummary(summary);
        entity.setChangeDiff(changeDiff);
        configAuditLogDao.insert(entity);
    }
}
