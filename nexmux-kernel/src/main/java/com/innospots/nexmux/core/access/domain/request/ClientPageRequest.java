package com.innospots.nexmux.core.access.domain.request;

import java.util.List;

/**
 * 客户端分页查询请求。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param status   状态（ENABLED / DISABLED），空为全部
 * @param name     名称模糊
 * @param code     编码模糊
 * @param tags     标签多选（取交集，内存过滤）
 */
public record ClientPageRequest(
        Long pageNo,
        Long pageSize,
        String status,
        String name,
        String code,
        List<String> tags) {
}
