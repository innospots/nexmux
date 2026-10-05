package com.innospots.nexmux.core.route.domain.request;

import java.util.List;

/**
 * 路由分页查询请求。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param status   状态（PUBLISHED / DISABLED / DRAFT），空为全部
 * @param type     类型（single / pool），按候选数内存过滤
 * @param tags     标签多选（取交集，内存过滤）
 */
public record RoutePageRequest(
        Long pageNo,
        Long pageSize,
        String status,
        String type,
        List<String> tags) {
}
