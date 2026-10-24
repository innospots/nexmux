package com.innospots.nexmux.inbound.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.thread.TLC;
import com.innospots.nexus.core.jaxrs.support.RequestProperties;
import com.innospots.nexus.core.jaxrs.support.RequestScope;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Runtime Ingress（nexmux-runtime-design §7.1）：OpenAI 兼容 {@code /v1/chat/completions}，
 * 仅在 {@code runtime} profile 装配。鉴权为 AppKey（Bearer / X-API-Key），不走控制台会话。
 *
 * <p>{@link NexusException} 由端点自捕获并映射为 OpenAI 形状错误体
 * （{@link RuntimeErrorResponses}）——不走平台全局 R&lt;T&gt; 异常映射，
 * 避免 Console 语义渗入数据面契约。
 */
@Path("/v1")
@Tag(name = "NexmuxRuntimeIngress", description = "模型运行时入口（OpenAI 兼容）")
public class RuntimeIngressEndpoint {

    /** 本端点固定入口路径（与 RouteSpec.ingress 匹配校验一致）。 */
    static final String INGRESS_PATH = "/v1/chat/completions";

    private final InvocationPipeline pipeline;

    public RuntimeIngressEndpoint(InvocationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nexmuxRuntimeChatCompletions", summary = "模型调用（OpenAI 兼容，model 填路由别名）")
    public Response chatCompletions(@HeaderParam("Authorization") String authorization,
                                    @HeaderParam("X-API-Key") String apiKey,
                                    Map<String, Object> body) {
        if (body == null) {
            return RuntimeErrorResponses.toResponse(
                    NexusException.build(NexmuxStatusCode.INVALID_REQUEST, "request body is required"));
        }
        String presentedKey = extractKey(authorization, apiKey);
        if (Boolean.TRUE.equals(body.get("stream"))) {
            StreamingOutput output = out -> writeStream(presentedKey, body, out);
            return Response.ok(output, "text/event-stream").build();
        }
        try {
            InvocationPipeline.InvocationResult result = pipeline.invoke(presentedKey, body, INGRESS_PATH);
            alignPlatformRequestId(result.context());
            // 上游原始 JSON 文本直接透传（String 实体避免二次编码）；状态码透传——
            // 200 为成功，4xx 为上游判定的接入方输入类错误（网关不吞成 502）
            Response.ResponseBuilder builder = Response.status(result.httpStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(result.body());
            if (result.context() != null) {
                // 排障对账头：与 mux_request_log.request_id 对应（仅已过关口、落库的请求有）
                builder.header("x-request-id", result.context().requestId());
            }
            return builder.build();
        } catch (NexusException e) {
            return RuntimeErrorResponses.toResponse(e);
        }
    }

    @POST
    @Path("/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nexmuxRuntimeResponses", summary = "OpenAI Responses 非流式调用")
    public Response responses(@HeaderParam("Authorization") String authorization,
                              @HeaderParam("X-API-Key") String apiKey, Map<String, Object> body) {
        return invokeProtocol(authorization, apiKey, body, "/responses", responsesRequest(body));
    }

    @POST
    @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nexmuxRuntimeMessages", summary = "Anthropic Messages 非流式调用")
    public Response messages(@HeaderParam("Authorization") String authorization,
                             @HeaderParam("X-API-Key") String apiKey, Map<String, Object> body) {
        return invokeProtocol(authorization, apiKey, body, "/messages", messagesRequest(body));
    }

    @POST
    @Path("/embeddings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nexmuxRuntimeEmbeddings", summary = "OpenAI Embeddings 调用")
    public Response embeddings(@HeaderParam("Authorization") String authorization,
                               @HeaderParam("X-API-Key") String apiKey, Map<String, Object> body) {
        return invokeProtocol(authorization, apiKey, body, "/embeddings", body);
    }

    private Response invokeProtocol(String authorization, String apiKey, Map<String, Object> original,
                                    String operation, Map<String, Object> requestBody) {
        if (original == null) {
            return RuntimeErrorResponses.toResponse(
                    NexusException.build(NexmuxStatusCode.INVALID_REQUEST, "request body is required"));
        }
        if (Boolean.TRUE.equals(original.get("stream"))) {
            return RuntimeErrorResponses.toResponse(NexusException.build(NexmuxStatusCode.INVALID_REQUEST,
                    operation + " streaming is not supported in this release"));
        }
        try {
            InvocationPipeline.InvocationResult result = pipeline.invokeOperation(
                    extractKey(authorization, apiKey), requestBody, "/v1" + operation, operation);
            alignPlatformRequestId(result.context());
            String responseBody = switch (operation) {
                case "/responses" -> responsesResponse(result.body());
                case "/messages" -> messagesResponse(result.body());
                default -> result.body();
            };
            Response.ResponseBuilder builder = Response.status(result.httpStatus())
                    .type(MediaType.APPLICATION_JSON).entity(responseBody);
            if (result.context() != null) {
                builder.header("x-request-id", result.context().requestId());
            }
            return builder.build();
        } catch (NexusException e) {
            return RuntimeErrorResponses.toResponse(e);
        }
    }

    private static Map<String, Object> responsesRequest(Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>(body);
        Object input = request.remove("input");
        Object instructions = request.remove("instructions");
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (instructions != null) {
            messages.add(Map.of("role", "system", "content", contentText(instructions)));
        }
        if (input instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> message && message.get("role") != null) {
                    messages.add(Map.of("role", String.valueOf(message.get("role")),
                            "content", contentText(message.get("content"))));
                } else {
                    messages.add(Map.of("role", "user", "content", contentText(item)));
                }
            }
        } else if (input != null) {
            messages.add(Map.of("role", "user", "content", contentText(input)));
        }
        request.put("messages", messages);
        request.remove("stream");
        return request;
    }

    private static Map<String, Object> messagesRequest(Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>(body);
        Object system = request.remove("system");
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (system != null) {
            messages.add(Map.of("role", "system", "content", contentText(system)));
        }
        Object input = request.get("messages");
        if (input instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> message && message.get("role") != null) {
                    messages.add(Map.of("role", String.valueOf(message.get("role")),
                            "content", contentText(message.get("content"))));
                }
            }
        }
        request.put("messages", messages);
        request.remove("stream");
        return request;
    }

    private static String contentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof List<?> parts) {
            return parts.stream().map(part -> part instanceof Map<?, ?> map
                    ? String.valueOf(map.get("text") != null ? map.get("text") : map.get("input_text"))
                    : String.valueOf(part)).reduce("", (left, right) -> left + right);
        }
        return String.valueOf(content);
    }

    private static String responsesResponse(String chatBody) {
        JsonNode chat = readJson(chatBody);
        String reply = chatText(chat, "content");
        long input = longValue(chat, "prompt_tokens");
        long output = longValue(chat, "completion_tokens");
        Map<String, Object> outputMessage = new LinkedHashMap<>();
        outputMessage.put("type", "message");
        outputMessage.put("id", "msg_nexmux");
        outputMessage.put("status", "completed");
        outputMessage.put("role", "assistant");
        outputMessage.put("content", List.of(Map.of("type", "output_text", "text", reply)));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", chat == null || !chat.hasNonNull("id") ? "resp_nexmux" : chat.get("id").asText());
        response.put("object", "response");
        response.put("created_at", java.time.Instant.now().getEpochSecond());
        response.put("status", "completed");
        response.put("model", chat == null ? "unknown" : chat.path("model").asText("unknown"));
        response.put("output", List.of(outputMessage));
        response.put("usage", Map.of("input_tokens", input, "output_tokens", output,
                "total_tokens", input + output));
        return NexmuxJson.write(response);
    }

    private static String messagesResponse(String chatBody) {
        JsonNode chat = readJson(chatBody);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", chat == null || !chat.hasNonNull("id") ? "msg_nexmux" : chat.get("id").asText());
        response.put("type", "message");
        response.put("role", "assistant");
        response.put("content", List.of(Map.of("type", "text", "text", chatText(chat, "content"))));
        response.put("model", chat == null ? "unknown" : chat.path("model").asText("unknown"));
        response.put("stop_reason", "end_turn");
        response.put("usage", Map.of("input_tokens", longValue(chat, "prompt_tokens"),
                "output_tokens", longValue(chat, "completion_tokens")));
        return NexmuxJson.write(response);
    }

    private static JsonNode readJson(String body) {
        try {
            return NexmuxJson.read(body, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String chatText(JsonNode chat, String field) {
        return chat == null ? "" : chat.path("choices").path(0).path("message").path(field).asText("");
    }

    private static long longValue(JsonNode chat, String field) {
        JsonNode usage = chat == null ? null : chat.get("usage");
        return usage == null ? 0 : usage.path(field).asLong(0);
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nexmuxRuntimeModels", summary = "可用模型清单（本 AppKey 已授权的已发布别名）")
    public Response models(@HeaderParam("Authorization") String authorization,
                           @HeaderParam("X-API-Key") String apiKey) {
        try {
            return Response.ok(pipeline.listModels(extractKey(authorization, apiKey)),
                    MediaType.APPLICATION_JSON).build();
        } catch (NexusException e) {
            return RuntimeErrorResponses.toResponse(e);
        }
    }

    private void writeStream(String presentedKey, Map<String, Object> body, OutputStream out)
            throws IOException {
        try {
            InvocationPipeline.InvocationResult result = pipeline.invokeStream(presentedKey, body, INGRESS_PATH, out);
            alignPlatformRequestId(result.context());
        } catch (com.innospots.nexmux.inbound.protocol.UpstreamCallException e) {
            // 上游 4xx：状态行已提交 200，上游错误体原样作为 SSE 事件带内透传
            out.write(("data: " + e.responseBody() + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (NexusException e) {
            // 流式失败：字节尚未写出时以 SSE 错误事件收尾（PRD §19.7 已开始输出不重试）
            out.write(("data: " + RuntimeErrorResponses.errorBodyJson(e) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /** 与平台响应过滤器共享同一 ID，避免其覆盖端点 Header 后与日志失去对应。 */
    private static void alignPlatformRequestId(InvocationPipeline.InvocationContext context) {
        if (context == null) {
            return;
        }
        String requestId = context.requestId();
        TLC.put(TLC.TRACE_ID, requestId);
        if (RequestScope.current() != null) {
            RequestScope.current().setProperty(RequestProperties.REQUEST_ID, requestId);
        }
    }

    /** Authorization: Bearer xxx 优先，其次 X-API-Key。 */
    static String extractKey(String authorization, String apiKey) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return apiKey == null ? null : apiKey.trim();
    }
}
