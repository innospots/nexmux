package com.innospots.nexmux.core.model.domain.request;

import java.util.List;

/**
 * 模型分页查询请求。
 *
 * @param pageNo    页码，从 1 开始
 * @param pageSize  每页条数
 * @param type      模型类型（LLM / Embedding / Image / Audio），空为全部
 * @param providers 厂商编码过滤（providerCode 集合），空为全部
 * @param keyword   搜索关键字（modelCode / displayName 模糊匹配），空为不过滤
 */
public record ModelPageRequest(
        Long pageNo,
        Long pageSize,
        String type,
        List<String> providers,
        String keyword) {
}
