package com.innospots.nexmux.inbound.ingress;

import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexus.base.exception.NexusException;
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
            pipeline.invokeStream(presentedKey, body, INGRESS_PATH, out);
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

    /** Authorization: Bearer xxx 优先，其次 X-API-Key。 */
    static String extractKey(String authorization, String apiKey) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return apiKey == null ? null : apiKey.trim();
    }
}
