package com.innospots.nexmux.inbound.protocol;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleChatAdapterTest {

    private final OpenAiCompatibleChatAdapter adapter = new OpenAiCompatibleChatAdapter();

    /** 本地上游：记录请求方法与 Authorization 头，返回固定 chat.completion。 */
    private HttpServer upstream(int status, AtomicReference<String> seenAuth,
                                AtomicReference<String> seenBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = status == 200
                    ? "{\"id\":\"c1\",\"object\":\"chat.completion\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8)
                    : "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void postsToChatCompletionsWithBearer() throws Exception {
        AtomicReference<String> seenAuth = new AtomicReference<>();
        AtomicReference<String> seenBody = new AtomicReference<>();
        HttpServer server = upstream(200, seenAuth, seenBody);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";

            String response = adapter.chatCompletions(baseUrl, "sk-runtime", "{\"model\":\"o4-mini\"}", 5000, 5000, null);

            assertThat(response).contains("\"object\":\"chat.completion\"");
            assertThat(seenAuth.get()).isEqualTo("Bearer sk-runtime");
            assertThat(seenBody.get()).contains("\"model\":\"o4-mini\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamPipesChunksVerbatimAndAggregatesUsage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                    + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            UpstreamChatAdapter.StreamOutcome outcome = adapter.streamChatCompletions(
                    baseUrl, "sk-runtime", "{\"model\":\"o4-mini\",\"stream\":true}", 5000, 5000, null, out);

            String sse = out.toString(StandardCharsets.UTF_8);
            // 上游 chunk 原样透传（含 [DONE]）
            assertThat(sse).contains("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}");
            assertThat(sse.trim()).endsWith("data: [DONE]");
            // 聚合：reply + usage + 合成 chat.completion
            assertThat(outcome.reply()).isEqualTo("你好");
            assertThat(outcome.tokensIn()).isEqualTo(3);
            assertThat(outcome.tokensOut()).isEqualTo(2);
            assertThat(outcome.ttftMs()).isGreaterThanOrEqualTo(0);
            assertThat(outcome.synthesizedBody()).contains("\"prompt_tokens\":3");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamHttpErrorThrowsBeforeWritingAnyBytes() throws Exception {
        AtomicReference<String> seenAuth = new AtomicReference<>();
        AtomicReference<String> seenBody = new AtomicReference<>();
        HttpServer server = upstream(429, seenAuth, seenBody);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            assertThatThrownBy(() -> adapter.streamChatCompletions(baseUrl, "sk-runtime", "{}", 5000, 5000, null, out))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTP 429");
            // 未向下游写出任何字节（调用方可安全回写错误形态）
            assertThat(out.size()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpErrorBecomesIOExceptionWithTruncatedBody() throws Exception {
        AtomicReference<String> seenAuth = new AtomicReference<>();
        AtomicReference<String> seenBody = new AtomicReference<>();
        HttpServer server = upstream(400, seenAuth, seenBody);
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            assertThatThrownBy(() ->
                    adapter.chatCompletions(baseUrl, "sk-runtime", "{}", 5000, 5000, null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTP 400")
                    .hasMessageContaining("bad request");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void proxyOfParsesHostPortForms() {
        // G-08：host:port / http://host:port（可带尾路径）
        assertThat(OpenAiCompatibleChatAdapter.proxyOf("127.0.0.1:8080").address())
                .isEqualTo(new InetSocketAddress("127.0.0.1", 8080));
        assertThat(OpenAiCompatibleChatAdapter.proxyOf("http://proxy.example.com:3128/").address())
                .isEqualTo(new InetSocketAddress("proxy.example.com", 3128));
        assertThatThrownBy(() -> OpenAiCompatibleChatAdapter.proxyOf("no-port"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
