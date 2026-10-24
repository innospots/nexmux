package com.innospots.nexmux.inbound.ingress;

import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexus.base.exception.NexusException;
import com.innospots.nexus.base.thread.TLC;
import com.innospots.nexus.core.jaxrs.support.RequestProperties;
import com.innospots.nexus.core.jaxrs.support.RequestScope;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeIngressEndpointContractTest {

    @Test
    void declaresChatCompletionsSubResource() throws Exception {
        Method method = RuntimeIngressEndpoint.class.getMethod(
                "chatCompletions", String.class, String.class, Map.class);
        assertThat(method.getAnnotation(POST.class)).isNotNull();
        assertThat(method.getAnnotation(Path.class).value()).isEqualTo("/chat/completions");
        assertThat(RuntimeIngressEndpoint.class.getAnnotation(Path.class).value()).isEqualTo("/v1");
    }

    @Test
    void extractsBearerKeyFirst() {
        assertThat(RuntimeIngressEndpoint.extractKey("Bearer gw-sk-a", "gw-sk-b")).isEqualTo("gw-sk-a");
        assertThat(RuntimeIngressEndpoint.extractKey(null, "gw-sk-b")).isEqualTo("gw-sk-b");
        assertThat(RuntimeIngressEndpoint.extractKey(null, null)).isNull();
    }

    @Test
    void errorBodyIsOpenAiShapedWithNexmuxCode() {
        NexusException e = NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "invalid app key");

        Response response = RuntimeErrorResponses.toResponse(e);

        assertThat(response.getStatus()).isEqualTo(401);
        String body = (String) response.getEntity();
        assertThat(body).contains("\"code\":\"NMX040002\"")
                .contains("\"type\":\"nexmux_error\"")
                .contains("invalid app key");
    }

    @Test
    void mapsAllRuntimeCodesToHttpStatus() {
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.APP_KEY_INVALID.fullCode()))
                .isEqualTo(401);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.ROUTE_ACCESS_DENIED.fullCode()))
                .isEqualTo(403);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.ROUTE_NOT_FOUND.fullCode()))
                .isEqualTo(404);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.QUOTA_EXCEEDED.fullCode()))
                .isEqualTo(429);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.RATE_LIMITED.fullCode()))
                .isEqualTo(429);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.NO_AVAILABLE_CANDIDATE.fullCode()))
                .isEqualTo(503);
        assertThat(RuntimeErrorResponses.httpStatusOf(NexmuxStatusCode.UPSTREAM_FAILURE.fullCode()))
                .isEqualTo(502);
        assertThat(RuntimeErrorResponses.httpStatusOf("UNKNOWN")).isEqualTo(500);
    }

    @Test
    void nullBodyReturns400InvalidRequest() {
        // 空请求体不得 NPE 漏到平台默认异常映射（数据面只出 OpenAI 形状错误体）
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);

        Response response = endpoint.chatCompletions("Bearer gw-sk-a", null, null);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) response.getEntity())
                .contains("\"code\":\"" + NexmuxStatusCode.INVALID_REQUEST.fullCode() + "\"");
        Mockito.verifyNoInteractions(pipeline);
    }

    @Test
    void passesThroughUpstream4xxWithRequestIdHeader() {
        // 上游 4xx：状态与错误体原样透传；x-request-id 供接入方报障对账
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Map<String, Object> body = Map.of("model", "general", "messages", List.of());
        InvocationPipeline.InvocationContext context = new InvocationPipeline.InvocationContext(
                "rq-x1", "t1", null, null, null, null, null, null, List.of());
        Mockito.when(pipeline.invoke(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new InvocationPipeline.InvocationResult(
                        400, "{\"error\":{\"message\":\"context too long\"}}", 5, -1, null, null, context));

        Response response = endpoint.chatCompletions("Bearer gw-sk-a", null, body);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) response.getEntity()).contains("context too long");
        assertThat(response.getHeaderString("x-request-id")).isEqualTo("rq-x1");
    }

    @Test
    void alignsPlatformRequestIdWithRuntimeLogId() {
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Map<String, Object> body = Map.of("model", "general", "messages", List.of());
        InvocationPipeline.InvocationContext context = new InvocationPipeline.InvocationContext(
                "rq-aligned", "trace-aligned", null, null, null, null, null, null, List.of());
        Mockito.when(pipeline.invoke(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new InvocationPipeline.InvocationResult(
                        200, "{\"ok\":true}", 5, 5, null, null, context));
        ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
        RequestScope.bind(request);
        try {
            endpoint.chatCompletions("Bearer gw-sk-a", null, body);

            Mockito.verify(request).setProperty(RequestProperties.REQUEST_ID, "rq-aligned");
            assertThat(TLC.getString(TLC.TRACE_ID)).isEqualTo("rq-aligned");
        } finally {
            RequestScope.clear();
            TLC.clear();
        }
    }

    @Test
    void mapsResponsesMessagesAndEmbeddingsContracts() {
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        InvocationPipeline.InvocationContext context = new InvocationPipeline.InvocationContext(
                "rq-protocol", "trace-protocol", null, null, null, null, null, null, List.of());
        String chat = "{\"id\":\"c1\",\"model\":\"stub-a\",\"choices\":[{\"message\":{\"content\":\"pong\"}}],"
                + "\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3}}";
        Mockito.when(pipeline.invokeOperation(Mockito.any(), Mockito.any(), Mockito.eq("/v1/responses"), Mockito.eq("/responses")))
                .thenReturn(new InvocationPipeline.InvocationResult(200, chat, 1, 1, 2L, 3L, context));
        Mockito.when(pipeline.invokeOperation(Mockito.any(), Mockito.any(), Mockito.eq("/v1/messages"), Mockito.eq("/messages")))
                .thenReturn(new InvocationPipeline.InvocationResult(200, chat, 1, 1, 2L, 3L, context));
        Mockito.when(pipeline.invokeOperation(Mockito.any(), Mockito.any(), Mockito.eq("/v1/embeddings"), Mockito.eq("/embeddings")))
                .thenReturn(new InvocationPipeline.InvocationResult(200,
                        "{\"object\":\"list\",\"data\":[{\"embedding\":[0.1]}]}", 1, 1, null, null, context));

        Response responses = endpoint.responses("Bearer gw-sk-a", null,
                Map.of("model", "general", "input", "ping"));
        Response messages = endpoint.messages("Bearer gw-sk-a", null,
                Map.of("model", "general", "messages", List.of(Map.of("role", "user", "content", "ping"))));
        Response embeddings = endpoint.embeddings("Bearer gw-sk-a", null,
                Map.of("model", "general", "input", "ping"));

        assertThat((String) responses.getEntity()).contains("\"object\":\"response\"")
                .contains("\"type\":\"output_text\"").contains("pong");
        assertThat((String) messages.getEntity()).contains("\"type\":\"message\"")
                .contains("\"stop_reason\":\"end_turn\"");
        assertThat((String) embeddings.getEntity()).contains("\"embedding\":[0.1]");
    }

    @Test
    void rateLimitedResponseCarriesRetryAfter() {
        // 分钟窗口限流：Retry-After 指向下一窗口（1..60 秒）
        NexusException e = NexusException.build(NexmuxStatusCode.RATE_LIMITED, "rate limited: r1");

        Response response = RuntimeErrorResponses.toResponse(e);

        assertThat(response.getStatus()).isEqualTo(429);
        String retryAfter = response.getHeaderString("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void modelsEndpointListsGrantedAliases() {
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Mockito.when(pipeline.listModels("gw-sk-a"))
                .thenReturn("{\"object\":\"list\",\"data\":[{\"id\":\"general\"}]}");
        Mockito.when(pipeline.listModels("gw-sk-bad"))
                .thenThrow(NexusException.build(NexmuxStatusCode.APP_KEY_INVALID, "invalid app key"));

        Response ok = endpoint.models("Bearer gw-sk-a", null);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat((String) ok.getEntity()).contains("\"id\":\"general\"");

        Response unauthorized = endpoint.models("Bearer gw-sk-bad", null);
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat((String) unauthorized.getEntity()).contains("\"code\":\"NMX040002\"");
    }

    @Test
    void streamPassesThroughUpstream4xxInBand() throws Exception {
        // 流式上游 4xx：状态行已提交，上游错误体以 SSE 事件带内透传
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Map<String, Object> body = Map.of("model", "general", "messages", List.of(), "stream", true);
        Mockito.doThrow(new com.innospots.nexmux.inbound.protocol.UpstreamCallException(400,
                        "{\"error\":{\"message\":\"context too long\"}}"))
                .when(pipeline).invokeStream(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        StreamingOutput output = (StreamingOutput) ((Response) endpoint.chatCompletions(null, "gw-sk-a", body))
                .getEntity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        output.write(out);

        String sse = out.toString(StandardCharsets.UTF_8);
        assertThat(sse).contains("context too long");
        assertThat(sse.trim()).endsWith("data: [DONE]");
    }

    @Test
    void nonStreamDelegatesToPipelineAndReturnsRawBody() {
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Map<String, Object> body = Map.of("model", "general", "messages", List.of());
        InvocationPipeline.InvocationResult result = new InvocationPipeline.InvocationResult(
                200, "{\"ok\":true}", 12, 12, 1L, 1L, null);
        Mockito.when(pipeline.invoke(Mockito.eq("gw-sk-a"), Mockito.eq(body),
                Mockito.eq("/v1/chat/completions"))).thenReturn(result);

        Response response = (Response) endpoint.chatCompletions("Bearer gw-sk-a", null, body);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void streamEmitsSseErrorEventOnFailure() throws Exception {
        InvocationPipeline pipeline = Mockito.mock(InvocationPipeline.class);
        RuntimeIngressEndpoint endpoint = new RuntimeIngressEndpoint(pipeline);
        Map<String, Object> body = Map.of("model", "general", "messages", List.of(), "stream", true);
        Mockito.doThrow(NexusException.build(NexmuxStatusCode.RATE_LIMITED, "rate limited: r1"))
                .when(pipeline).invokeStream(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        StreamingOutput output = (StreamingOutput) ((Response) endpoint.chatCompletions(null, "gw-sk-a", body))
                .getEntity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        output.write(out);

        String sse = out.toString(StandardCharsets.UTF_8);
        assertThat(sse).contains("\"code\":\"NMX100002\"").contains("rate limited");
        assertThat(sse.trim()).endsWith("data: [DONE]");
    }
}
