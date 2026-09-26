package com.innospots.nexmux.core.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestLogQueryService {

    private final RequestLogDao requestLogDao;

    public PageResult<RequestLogVo> page(RequestLogPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = new LambdaQueryWrapper<RequestLogEntity>().orderByDesc(RequestLogEntity::getRequestedAt);
        long total = requestLogDao.selectCount(wrapper);
        var records = requestLogDao.selectList(wrapper.last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public RequestLogVo get(String requestId) {
        RequestLogEntity entity = requestLogDao.selectById(requestId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "request log not found");
        }
        return toVo(entity);
    }

    private RequestLogVo toVo(RequestLogEntity entity) {
        return new RequestLogVo(
                entity.getRequestId(),
                entity.getClientId(),
                entity.getRouteId(),
                entity.getModelCode(),
                entity.getStatus(),
                entity.getLatencyMs(),
                entity.getErrorCode(),
                entity.getRouteTrace(),
                entity.getRequestedAt());
    }
}
