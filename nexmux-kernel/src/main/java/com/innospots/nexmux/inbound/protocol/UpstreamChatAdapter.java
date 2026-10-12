package com.innospots.nexmux.inbound.protocol;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 上游对话协议适配（数据面）。
 *
 * <p>上游凭证只允许在本层消费（ADR-0001 §4：Runtime 持 Credential Cache，
 * Console 只保存密文配置、不参与推理链路）。
 */
public interface UpstreamChatAdapter {

    /**
     * POST {@code {baseUrl}/chat/completions}（OpenAI 兼容，非流式）。
     *
     * @param baseUrl         供应商基础地址（可含或不含结尾 {@code /}）
     * @param apiKey          上游凭证明文（数据面 Credential Cache 提供）
     * @param requestBodyJson 完整请求体 JSON（{@code model} 已由调用方替换为上游模型 ID）
     * @param timeoutMs       连接 / 读取超时（毫秒）
     * @return 上游原始响应体
     * @throws IOException 网络失败或 HTTP 状态 &ge; 400（消息含状态码与截断的上游错误体）
     */
    String chatCompletions(String baseUrl, String apiKey, String requestBodyJson, int timeoutMs)
            throws IOException;

    /**
     * POST {@code {baseUrl}/chat/completions}（SSE 流式）：上游 SSE 逐行原样透传至
     * {@code out}（含上游 {@code [DONE]}），同时聚合 reply 与 usage 供计量落库。
     * 连接失败 / HTTP &ge; 400 在写出任何字节前抛 {@link IOException}（由调用方决定错误形态）。
     *
     * @param requestBodyJson 请求体 JSON（调用方已置 {@code stream=true} 与 usage 选项）
     */
    StreamOutcome streamChatCompletions(String baseUrl, String apiKey, String requestBodyJson,
                                        int timeoutMs, OutputStream out) throws IOException;

    /**
     * 流式调用聚合结果。
     *
     * @param reply           聚合的文本回复
     * @param tokensIn        输入 tokens（usage chunk，可空）
     * @param tokensOut       输出 tokens（可空）
     * @param ttftMs          首字节延迟（毫秒；-1 表示无内容 chunk）
     * @param synthesizedBody 聚合为 chat.completion 形态的 JSON（计量 usage 解析复用）
     */
    record StreamOutcome(String reply, Long tokensIn, Long tokensOut, long ttftMs,
                         String synthesizedBody) {
    }
}
