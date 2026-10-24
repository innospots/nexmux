package com.innospots.nexmux.core.ops.domain.request;

/**
 * 请求日志分页查询请求。
 *
 * @param pageNo     页码，从 1 开始
 * @param pageSize   每页条数
 * @param start      起始日期（yyyy-MM-dd，含）
 * @param end        截止日期（yyyy-MM-dd，含）
 * @param status     状态（SUCCESS / ERROR / UNFINISHED / CANCELLED / OTHER）
 * @param model      模型编码
 * @param provider   供应商编码
 * @param protocol   协议格式
 * @param routeGroup 路由分组
 * @param email      用户 Email 模糊
 * @param appKey     AppKey ID 模糊
 */
public record RequestLogPageRequest(
        Long pageNo,
        Long pageSize,
        String start,
        String end,
        String status,
        String model,
        String provider,
        String protocol,
        String routeGroup,
        String email,
        String appKey) {
}
