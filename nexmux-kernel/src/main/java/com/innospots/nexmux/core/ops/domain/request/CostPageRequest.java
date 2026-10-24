package com.innospots.nexmux.core.ops.domain.request;

/**
 * 成本明细分页查询请求（成功且已计费的请求日志视图）。
 *
 * @param pageNo      页码，从 1 开始
 * @param pageSize    每页条数
 * @param application Application 模糊
 * @param appKey      AppKey ID 模糊
 * @param alias       路由别名模糊
 * @param model       模型编码
 * @param provider    供应商编码
 * @param start       起始日期（yyyy-MM-dd，含）
 * @param end         截止日期（yyyy-MM-dd，含）
 */
public record CostPageRequest(
        Long pageNo,
        Long pageSize,
        String application,
        String appKey,
        String alias,
        String model,
        String provider,
        String start,
        String end) {
}
