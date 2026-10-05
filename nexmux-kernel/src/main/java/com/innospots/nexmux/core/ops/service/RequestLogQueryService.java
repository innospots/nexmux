package com.innospots.nexmux.core.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innospots.nexus.base.domain.response.PageResult;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexmux.core.ops.dao.RequestLogDao;
import com.innospots.nexmux.core.ops.domain.entity.RequestLogEntity;
import com.innospots.nexmux.core.ops.domain.request.RequestLogPageRequest;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogDetailVo;
import com.innospots.nexmux.core.ops.domain.vo.RequestLogVo;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestLogQueryService {

    private final RequestLogDao requestLogDao;

    public PageResult<RequestLogVo> page(RequestLogPageRequest request) {
        long pageNo = request.pageNo() == null ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null ? 20L : request.pageSize();
        var wrapper = buildWrapper(request);
        long total = requestLogDao.selectCount(wrapper);
        var records = requestLogDao.selectList(wrapper
                        .orderByDesc(RequestLogEntity::getRequestedAt)
                        .last("LIMIT " + pageSize + " OFFSET " + (pageNo - 1) * pageSize))
                .stream()
                .map(this::toVo)
                .toList();
        return PageResult.of(records, pageNo, pageSize, total);
    }

    public RequestLogDetailVo get(String requestId) {
        RequestLogEntity entity = requestLogDao.selectById(requestId);
        if (entity == null) {
            throw NexusException.build(NexmuxStatusCode.RESOURCE_NOT_FOUND, "request log not found");
        }
        return new RequestLogDetailVo(
                entity.getRequestId(),
                entity.getClientId(),
                entity.getRouteId(),
                entity.getModelCode(),
                entity.getStatus(),
                entity.getLatencyMs(),
                entity.getErrorCode(),
                entity.getRouteTrace(),
                entity.getRequestedAt(),
                entity.getAppKeyId(),
                entity.getUserEmail(),
                entity.getProviderCode(),
                entity.getProtocol(),
                entity.getRouteGroup(),
                entity.getApplication(),
                entity.getAlias(),
                entity.getHttpStatus(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getCacheReadTokens(),
                entity.getCacheWriteTokens(),
                entity.getCostUsd(),
                entity.getBillingUsd(),
                entity.getTtftMs(),
                entity.getUpstreamModel(),
                entity.getUpstreamEndpoint(),
                entity.getUpstreamStatus(),
                entity.getUpstreamLatencyMs(),
                entity.getRetries(),
                entity.getClientIp(),
                entity.getIngressMethod(),
                entity.getIngressPath(),
                entity.getTraceId(),
                entity.getErrorType(),
                entity.getPricingSnapshot(),
                entity.getPromptSnapshot(),
                entity.getCreatedAt());
    }

    private LambdaQueryWrapper<RequestLogEntity> buildWrapper(RequestLogPageRequest request) {
        var wrapper = new LambdaQueryWrapper<RequestLogEntity>();
        if (request.start() != null && !request.start().isBlank()) {
            wrapper.ge(RequestLogEntity::getRequestedAt,
                    LocalDate.parse(request.start().trim()).atStartOfDay());
        }
        if (request.end() != null && !request.end().isBlank()) {
            wrapper.le(RequestLogEntity::getRequestedAt,
                    LocalDate.parse(request.end().trim()).atTime(LocalTime.MAX));
        }
        if (request.status() != null && !request.status().isBlank()) {
            wrapper.eq(RequestLogEntity::getStatus, request.status());
        }
        if (request.model() != null && !request.model().isBlank()) {
            wrapper.eq(RequestLogEntity::getModelCode, request.model());
        }
        if (request.provider() != null && !request.provider().isBlank()) {
            wrapper.eq(RequestLogEntity::getProviderCode, request.provider());
        }
        if (request.protocol() != null && !request.protocol().isBlank()) {
            wrapper.eq(RequestLogEntity::getProtocol, request.protocol());
        }
        if (request.routeGroup() != null && !request.routeGroup().isBlank()) {
            wrapper.eq(RequestLogEntity::getRouteGroup, request.routeGroup());
        }
        if (request.email() != null && !request.email().isBlank()) {
            wrapper.like(RequestLogEntity::getUserEmail, request.email().trim());
        }
        if (request.appKey() != null && !request.appKey().isBlank()) {
            wrapper.like(RequestLogEntity::getAppKeyId, request.appKey().trim());
        }
        return wrapper;
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
                entity.getRequestedAt(),
                entity.getAppKeyId(),
                entity.getUserEmail(),
                entity.getProviderCode(),
                entity.getProtocol(),
                entity.getRouteGroup(),
                entity.getApplication(),
                entity.getAlias(),
                entity.getHttpStatus(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getCacheReadTokens(),
                entity.getCacheWriteTokens(),
                entity.getCostUsd(),
                entity.getBillingUsd());
    }
}
