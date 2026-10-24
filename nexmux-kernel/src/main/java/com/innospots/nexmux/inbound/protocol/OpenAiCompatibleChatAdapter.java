package com.innospots.nexmux.inbound.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容上游适配（{@code POST {baseUrl}/chat/completions}，非流式骨架）。
 * SSE 流式与更多协议（Responses / Anthropic 等）由后续批次补齐。
 */
public final class OpenAiCompatibleChatAdapter implements UpstreamChatAdapter {

    @Override
    public String chatCompletions(String baseUrl, String apiKey, String requestBodyJson,
                                  int readTimeoutMs, int connectTimeoutMs, String proxy)
            throws IOException {
        return post("/chat/completions", baseUrl, apiKey, requestBodyJson,
                readTimeoutMs, connectTimeoutMs, proxy);
    }

    @Override
    public String post(String operation, String baseUrl, String apiKey, String requestBodyJson,
                       int readTimeoutMs, int connectTimeoutMs, String proxy) throws IOException {
        HttpURLConnection connection = connect(operation, baseUrl, apiKey, requestBodyJson,
                readTimeoutMs, connectTimeoutMs, proxy);
        try {
            InputStream in = connection.getInputStream();
            return in == null
                    ? "{\"error\":\"empty upstream response\"}"
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public StreamOutcome streamChatCompletions(String baseUrl, String apiKey, String requestBodyJson,
                                               int readTimeoutMs, int connectTimeoutMs, String proxy,
                                               OutputStream out) throws IOException {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = connect("/chat/completions", baseUrl, apiKey, requestBodyJson,
                readTimeoutMs, connectTimeoutMs, proxy);
        long ttftMs = -1;
        StringBuilder reply = new StringBuilder();
        Long tokensIn = null;
        Long tokensOut = null;
        String upstreamModel = readModel(requestBodyJson);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (ttftMs < 0 && !line.isBlank()) {
                    ttftMs = System.currentTimeMillis() - start;
                }
                if (!line.startsWith("data: ")) {
                    continue;
                }
                JsonNode chunk = readJsonTolerant(line.substring(6));
                if (chunk == null) {
                    continue;
                }
                JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                if (delta.isTextual()) {
                    reply.append(delta.asText());
                }
                JsonNode usage = chunk.get("usage");
                if (usage != null && usage.isObject()) {
                    tokensIn = longOrNull(usage, "prompt_tokens");
                    tokensOut = longOrNull(usage, "completion_tokens");
                }
            }
            return new StreamOutcome(reply.toString(), tokensIn, tokensOut, ttftMs,
                    synthesize(upstreamModel, reply, tokensIn, tokensOut));
        } finally {
            connection.disconnect();
        }
    }

    /** 建立连接并写请求体；HTTP &ge; 400 读错误流抛 {@link UpstreamCallException}（连接已断开，未向下游写字节）。 */
    private HttpURLConnection connect(String operation, String baseUrl, String apiKey, String requestBodyJson,
                                      int readTimeoutMs, int connectTimeoutMs, String proxy)
            throws IOException {
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + operation);
        // G-08：Provider proxy 生效（仅 HTTP 代理，host:port / http://host:port）
        HttpURLConnection connection = proxy == null || proxy.isBlank()
                ? (HttpURLConnection) uri.toURL().openConnection()
                : (HttpURLConnection) uri.toURL().openConnection(proxyOf(proxy));
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setDoOutput(true);
        byte[] bytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        int status = connection.getResponseCode();
        if (status >= 400) {
            InputStream err = connection.getErrorStream();
            String response = err == null
                    ? "{\"error\":\"empty upstream response\"}"
                    : new String(err.readAllBytes(), StandardCharsets.UTF_8);
            connection.disconnect();
            throw new UpstreamCallException(status, abbreviate(response));
        }
        return connection;
    }

    /** 解析代理配置（{@code host:port} 或 {@code http://host:port}，可带尾路径/斜杠）。 */
    static java.net.Proxy proxyOf(String proxy) {
        String normalized = proxy.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "");
        int colon = normalized.lastIndexOf(':');
        if (colon <= 0 || colon == normalized.length() - 1) {
            throw new IllegalArgumentException("invalid proxy (expect host:port): " + proxy);
        }
        return new java.net.Proxy(java.net.Proxy.Type.HTTP,
                new java.net.InetSocketAddress(
                        normalized.substring(0, colon),
                        Integer.parseInt(normalized.substring(colon + 1))));
    }

    private static String readModel(String requestBodyJson) {
        JsonNode body = readJsonTolerant(requestBodyJson);
        JsonNode model = body == null ? null : body.get("model");
        return model != null && model.isTextual() ? model.asText() : "unknown";
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isNumber()
                ? node.get(field).longValue() : null;
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

    /** 流式聚合为 chat.completion 形态（计量 usage 解析与返回报文留存复用）。 */
    private static String synthesize(String model, StringBuilder reply, Long tokensIn, Long tokensOut) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("object", "chat.completion");
        body.put("model", model);
        body.put("choices", List.of(Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", reply.toString()),
                "finish_reason", "stop")));
        if (tokensIn != null || tokensOut != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", tokensIn == null ? 0 : tokensIn);
            usage.put("completion_tokens", tokensOut == null ? 0 : tokensOut);
            body.put("usage", usage);
        }
        return NexmuxJson.write(body);
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
