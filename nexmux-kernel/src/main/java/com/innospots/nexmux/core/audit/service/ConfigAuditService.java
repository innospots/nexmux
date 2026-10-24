package com.innospots.nexmux.core.audit.service;

import com.innospots.nexus.base.thread.SessionContext;
import com.innospots.nexmux.core.audit.dao.ConfigAuditLogDao;
import com.innospots.nexmux.core.audit.domain.entity.ConfigAuditLogEntity;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConfigAuditService {

    private final ConfigAuditLogDao configAuditLogDao;

    /**
     * 记录配置审计（操作人：显式传入优先，缺省取当前会话用户）。
     */
    public void record(String resourceType, String resourceId, String operation,
                       String summary, String changeDiff) {
        record(resourceType, resourceId, operation, summary, changeDiff, null);
    }

    public void record(String resourceType, String resourceId, String operation,
                       String summary, String changeDiff, String operatorId) {
        ConfigAuditLogEntity entity = new ConfigAuditLogEntity();
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setOperation(operation);
        entity.setChangeSummary(summary);
        entity.setChangeDiff(changeDiff);
        entity.setOperatorId(operatorId != null && !operatorId.isBlank()
                ? operatorId
                : SessionContext.user().map(u -> String.valueOf(u.userId())).orElse(null));
        configAuditLogDao.insert(entity);
    }
}
