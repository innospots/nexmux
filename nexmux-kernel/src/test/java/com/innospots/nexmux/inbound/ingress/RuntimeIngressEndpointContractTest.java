package com.innospots.nexmux.inbound.ingress;

import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexmux.inbound.pipeline.InvocationPipeline;
import com.innospots.nexus.base.exception.NexusException;
import jakarta.ws.rs.POST;
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
