package com.innospots.nexmux.core.access.domain.request;

import java.util.List;

/**
 * 客户端模板分页查询请求。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param status   状态（ENABLED / DISABLED），空为全部
 * @param keyword  名称 / 编码模糊
 * @param tags     标签多选（取交集，内存过滤）
 */
public record ClientTemplatePageRequest(
        Long pageNo,
        Long pageSize,
        String status,
        String keyword,
        List<String> tags) {
}
