package com.innospots.nexmux.core.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.domain.request.CostPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.CostRecordVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;

/**
 * 成本查询：成功且已计费请求日志的只读视图（无独立成本表，设计 §13）。
 */
@RequiredArgsConstructor
public class CostQueryService {

    private final RequestLogDao requestLogDao;

    public PageResult<CostRecordVo> page(CostPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = billedWrapper();
        if (request.application() != null && !request.application().isBlank()) {
            wrapper.like(RequestLogEntity::getApplication, request.application().trim());
        }
        if (request.appKey() != null && !request.appKey().isBlank()) {
            wrapper.like(RequestLogEntity::getAppKeyId, request.appKey().trim());
        }
        if (request.alias() != null && !request.alias().isBlank()) {
            wrapper.like(RequestLogEntity::getAlias, request.alias().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            wrapper.eq(RequestLogEntity::getModelCode, request.model());
        }
        if (request.provider() != null && !request.provider().isBlank()) {
            wrapper.eq(RequestLogEntity::getProviderCode, request.provider());
        }
        if (request.start() != null && !request.start().isBlank()) {
            wrapper.ge(RequestLogEntity::getRequestedAt,
                    LocalDate.parse(request.start().trim()).atStartOfDay());
        }
        if (request.end() != null && !request.end().isBlank()) {
            wrapper.le(RequestLogEntity::getRequestedAt,
                    LocalDate.parse(request.end().trim()).atTime(LocalTime.MAX));
        }
        long total = requestLogDao.selectCount(wrapper);
        var records = requestLogDao.selectList(wrapper
                        .orderByDesc(RequestLogEntity::getRequestedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public CostRecordVo get(String requestId) {
        RequestLogEntity entity = requestLogDao.selectById(requestId);
        if (entity == null || !"SUCCESS".equals(entity.getStatus())) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "cost record not found");
        }
        return toVo(entity);
    }

    private static LambdaQueryWrapper<RequestLogEntity> billedWrapper() {
        return new LambdaQueryWrapper<RequestLogEntity>()
                .eq(RequestLogEntity::getStatus, "SUCCESS")
                .isNotNull(RequestLogEntity::getCostUsd);
    }

    private CostRecordVo toVo(RequestLogEntity entity) {
        return new CostRecordVo(
                entity.getRequestId(),
                entity.getRequestedAt(),
                entity.getApplication(),
                entity.getClientId(),
                entity.getAppKeyId(),
                entity.getAlias(),
                entity.getModelCode(),
                entity.getProviderCode(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getCacheReadTokens(),
                entity.getCostUsd(),
                entity.getBillingUsd(),
                entity.getPricingSnapshot());
    }
}
