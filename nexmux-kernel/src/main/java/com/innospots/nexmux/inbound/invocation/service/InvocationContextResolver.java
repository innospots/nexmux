package com.innospots.nexmux.inbound.invocation.service;

import com.innospots.nexmux.core.access.domain.entity.ClientEntity;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.provider.domain.entity.ProviderEntity;
import com.innospots.nexmux.core.route.domain.selection.CandidateSelector;
import com.innospots.nexmux.core.route.domain.value.RouteSpec;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.catalog.service.PublishedRouteCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeCatalogCache;
import com.innospots.nexmux.inbound.catalog.service.RuntimeRouter;
import com.innospots.nexmux.inbound.invocation.domain.model.InvocationContext;
import com.innospots.nexus.base.exception.NexusException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 调用链路解析：鉴权 → 路由 → 授权 → 配额预检 → 健康预过滤 → 候选 → Binding → 供应商。
 *
 * <p>只做解析与校验，不产生上游副作用；任一关口不通过即抛 {@link NexusException}，
 * 由入口端映射为 OpenAI 形状错误体。配额在此只检查不记账——被拒绝的请求不消耗配额。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see InvocationContext
 * @see ModelBindingResolver
 */
@RequiredArgsConstructor
public final class InvocationContextResolver {

    private static final String PROVIDER_ENABLED = "ENABLED";
    private static final String MODEL_ENABLED = "ENABLED";

    private final RuntimeCatalogCache runtimeCatalogCache;
    private final RuntimeRouter runtimeRouter;
    private final AppKeyVerifier appKeyVerifier;
    private final QuotaGate quotaGate;
    private final ModelBindingResolver bindingResolver;

    /**
     * 解析一次调用。
     *
     * @param presentedKey 请求头中的 AppKey 明文
     * @param requestBody  请求体（读取 {@code model} 与 {@code user}）
     * @param ingressPath  入口路径（与路由 {@code ingress} 匹配校验）
     * @return 解析上下文
     * @throws NexusException 鉴权 / 路由 / 授权 / 配额 / 选路任一环节失败
     */
    public InvocationContext resolve(String presentedKey, Map<String, Object> requestBody,
                                     String ingressPath) {
        ClientEntity client = appKeyVerifier.authenticate(presentedKey);
        String alias = textValue(requestBody.get("model"));
        if (alias == null) {
            throw NexusException.build(NexmuxStatusCode.INVALID_REQUEST,
                    "model (route alias) is required");
        }
        PublishedRouteCache.PublishedRoute route = runtimeRouter.routeCache().findByAlias(alias)
                .filter(published -> published.spec().ingress() == null
                        || published.spec().ingress().isBlank()
                        || published.spec().ingress().equals(ingressPath))
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.ROUTE_NOT_FOUND,
                        "no published route for alias: " + alias));
        if (!NexmuxJson.readStringList(client.getAuthorizedRouteIds()).contains(route.routeId())) {
            throw NexusException.build(NexmuxStatusCode.ROUTE_ACCESS_DENIED,
                    "route is not granted to this client");
        }
        // 只检查不记账：REQUEST 计数在全部关口通过、即将调上游时记
        List<String> softQuota = quotaGate.check(client);

        RouteSpec spec = route.spec();
        Set<String> unhealthy = bindingResolver.unhealthyModelCodes(spec, runtimeCatalogCache::findModel);
        Set<String> disabledModels = disabledModelCodes(spec);
        String sessionKey = textValue(requestBody.get("user")) != null
                ? textValue(requestBody.get("user")) : client.getClientCode();
        CandidateSelector.Selection selection =
                runtimeRouter.resolveByAlias(alias, disabledModels, unhealthy, sessionKey)
                        .orElseThrow(() -> NexusException.build(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                                "no available candidate in route: " + alias));
        ModelEntity model = runtimeCatalogCache.findModel(selection.candidate().modelCode())
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                        "candidate model not found: " + selection.candidate().modelCode()));
        // Binding 级健康过滤跟随路由快照开关（不过滤时选中不健康绑定将真实调上游并失败）
        ModelBindingResolver.ResolvedBinding binding = ModelBindingResolver.withCandidateRates(
                bindingResolver.select(model, spec.filterUnhealthyBindings())
                        .orElseThrow(() -> NexusException.build(
                                NexmuxStatusCode.NO_AVAILABLE_CANDIDATE,
                                "no healthy binding for model: " + model.getModelCode())),
                selection.candidate());
        ProviderEntity provider = runtimeCatalogCache.findProvider(binding.providerId())
                .filter(item -> PROVIDER_ENABLED.equals(item.getStatus()))
                .orElseThrow(() -> NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                        "provider not available: " + binding.providerId()));
        requireSupportedProtocol(provider, binding);

        return new InvocationContext(newRequestId(), randomTraceId(), client, route, selection,
                model, binding, provider, softQuota);
    }

    /** 停用模型不参与选路（即时止损，与绑定健康过滤独立、恒生效）。 */
    private Set<String> disabledModelCodes(RouteSpec spec) {
        Set<String> disabled = new HashSet<>();
        for (var candidate : spec.candidates()) {
            if (candidate.enabled() != null && !candidate.enabled()) {
                continue;
            }
            runtimeCatalogCache.findModel(candidate.modelCode())
                    .filter(model -> !MODEL_ENABLED.equals(model.getStatus()))
                    .ifPresent(model -> disabled.add(model.getModelCode()));
        }
        return disabled;
    }

    /** 单协议阶段明确拒绝其他协议，避免以错误报文格式调用上游。 */
    private static void requireSupportedProtocol(ProviderEntity provider,
                                                 ModelBindingResolver.ResolvedBinding binding) {
        String configured = binding.upstreamProtocol() == null || binding.upstreamProtocol().isBlank()
                ? provider.getDefaultProtocol() : binding.upstreamProtocol();
        String protocol = configured == null ? "openai-completions"
                : configured.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (!"openai-completions".equals(protocol) && !"openai-chat-completions".equals(protocol)
                && !"openai".equals(protocol) && !"openai/chatcompletions".equals(protocol)) {
            throw NexusException.build(NexmuxStatusCode.INVALID_REQUEST,
                    "upstream protocol is not supported by the OpenAI chat adapter");
        }
    }

    private static String newRequestId() {
        return "rq-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String randomTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String textValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
