package com.innospots.nexmux.inbound.invocation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.model.domain.entity.ModelEntity;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上游请求体组装：客户端入参 + Model 默认参数，并替换上游模型标识与流式开关。
 *
 * <p>合并顺序为 Model 默认参数 → 客户端显式参数 → 覆盖 {@code model}/{@code stream}，
 * 保证接入方无法越权指定上游模型，也保证流式选项与调用方式一致。</p>
 *
 * @author Smars
 * @date 2026/11/22
 * @see ModelEntity
 */
public final class UpstreamRequestBuilder {

    private UpstreamRequestBuilder() {
    }

    /**
     * 组装上游请求体。
     *
     * @param requestBody 客户端原始请求体
     * @param model       选中的本地模型（提供默认参数）
     * @param binding     选中的上游绑定（提供上游模型 ID）
     * @param stream      是否流式
     * @return 新的请求体（不修改入参）
     */
    public static Map<String, Object> build(Map<String, Object> requestBody, ModelEntity model,
                                            ModelBindingResolver.ResolvedBinding binding, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        mergeDefaultParams(body, model.getDefaultParams());
        body.putAll(requestBody);
        body.put("model", binding.providerModelId());
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        } else {
            body.remove("stream_options");
        }
        return body;
    }

    private static void mergeDefaultParams(Map<String, Object> body, String defaultParams) {
        JsonNode defaults = readJsonTolerant(defaultParams);
        if (defaults == null || !defaults.isObject()) {
            return;
        }
        putDefault(body, defaults, "temperature", "temperature");
        putDefault(body, defaults, "topP", "top_p");
        putDefault(body, defaults, "topK", "top_k");
        putDefault(body, defaults, "maxTokens", "max_tokens");
        putDefault(body, defaults, "max_tokens", "max_tokens");
        putDefault(body, defaults, "frequencyPenalty", "frequency_penalty");
        putDefault(body, defaults, "presencePenalty", "presence_penalty");
        putDefault(body, defaults, "reasoningEffort", "reasoning_effort");
        putDefault(body, defaults, "seed", "seed");
        if (!body.containsKey("stop") && defaults.has("stopSequences")) {
            JsonNode value = defaults.get("stopSequences");
            if (value.isArray()) {
                body.put("stop", value);
            } else if (value.isTextual() && !value.asText().isBlank()) {
                body.put("stop", NexmuxJson.read(value.asText(), Object.class));
            }
        }
        if (!body.containsKey("response_format") && defaults.has("responseFormat")) {
            JsonNode value = defaults.get("responseFormat");
            if (value.isObject()) {
                body.put("response_format", value);
            } else if (value.isTextual() && !value.asText().isBlank()) {
                body.put("response_format", Map.of("type", value.asText()));
            }
        }
    }

    private static void putDefault(Map<String, Object> body, JsonNode defaults,
                                   String sourceField, String targetField) {
        if (!body.containsKey(targetField) && defaults.hasNonNull(sourceField)) {
            body.put(targetField, defaults.get(sourceField));
        }
    }

    private static JsonNode readJsonTolerant(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
