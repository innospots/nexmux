package com.innospots.nexmux.inbound.invocation.domain.model;

import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.domain.selection.CandidateSelector;
import com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache;
import com.innospots.nexmux.inbound.invocation.service.ModelBindingResolver;
import java.util.List;

/**
 * 一次调用的解析上下文（计量与 routeTrace 使用）。
 *
 * @param requestId         网关请求 ID（与 {@code x-request-id} 及日志一致）
 * @param traceId           链路追踪 ID
 * @param client            已鉴权客户端
 * @param route             命中的已发布路由
 * @param selection         候选选择结果
 * @param model             选中的本地模型
 * @param binding           选中的上游绑定
 * @param provider          上游供应商
 * @param softQuotaTriggered 触发的软配额规则名（无触发为空表）
 * @author Smars
 * @date 2026/11/22
 * @see ModelBindingResolver.ResolvedBinding
 */
public record InvocationContext(
        String requestId,
        String traceId,
        ClientEntity client,
        PublishedRouteCache.PublishedRoute route,
        CandidateSelector.Selection selection,
        ModelEntity model,
        ModelBindingResolver.ResolvedBinding binding,
        ProviderEntity provider,
        List<String> softQuotaTriggered) {
}
