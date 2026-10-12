package com.innospots.nexmux.inbound.ingress;

import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexus.base.exception.NexusException;
import jakarta.ws.rs.Consumes;
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
        String presentedKey = extractKey(authorization, apiKey);
        if (Boolean.TRUE.equals(body.get("stream"))) {
            StreamingOutput output = out -> writeStream(presentedKey, body, out);
            return Response.ok(output, "text/event-stream").build();
        }
        try {
            InvocationPipeline.InvocationResult result = pipeline.invoke(presentedKey, body, INGRESS_PATH);
            // 非流式：上游原始 JSON 文本直接透传（String 实体避免二次编码）
            return Response.ok(result.body(), MediaType.APPLICATION_JSON).build();
        } catch (NexusException e) {
            return RuntimeErrorResponses.toResponse(e);
        }
    }

    private void writeStream(String presentedKey, Map<String, Object> body, OutputStream out)
            throws IOException {
        try {
            pipeline.invokeStream(presentedKey, body, INGRESS_PATH, out);
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
