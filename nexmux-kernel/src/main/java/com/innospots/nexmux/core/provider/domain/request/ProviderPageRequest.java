package com.innospots.nexmux.core.provider.domain.request;

import java.util.List;

/**
 * 提供方分页查询请求。
 *
 * @param pageNo    页码，从 1 开始
 * @param pageSize  每页条数
 * @param keyword   关键字（名称 / 编码 / baseUrl 模糊）
 * @param status    启停状态（ENABLED / DISABLED），空为全部
 * @param keyStatus 密钥状态（pending / none），空为不过滤
 * @param types     厂商编码过滤（vendorCode 集合），空为全部
 */
public record ProviderPageRequest(
        Long pageNo,
        Long pageSize,
        String keyword,
        String status,
        String keyStatus,
        List<String> types) {
}
