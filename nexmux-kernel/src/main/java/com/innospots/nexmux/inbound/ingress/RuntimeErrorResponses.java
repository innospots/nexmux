package com.innospots.nexmux.inbound.ingress;

import com.innospots.nexmux.core.shared.json.NexmuxJson;
import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime 错误响应工厂（nexmux-runtime-design §7.1/§8）：NexusException → OpenAI 兼容
 * 错误体 {@code {"error": {message, type, code}}}（code 填 Nexmux 状态码 fullCode），
 * HTTP 状态取自状态码映射。端点自捕获使用；SSE 错误事件复用 {@link #errorBodyJson}。
 */
public final class RuntimeErrorResponses {

    private RuntimeErrorResponses() {
    }

    /** 构建 OpenAI 形状的错误 Response。 */
    public static Response toResponse(NexusException e) {
        return Response.status(httpStatusOf(e.code()))
                .type(MediaType.APPLICATION_JSON)
                .entity(errorBodyJson(e))
                .build();
    }

    /** OpenAI 兼容错误体 JSON（SSE 错误事件复用同一形状）。 */
    public static String errorBodyJson(NexusException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", e.getMessage());
        error.put("type", "nexmux_error");
        error.put("code", e.code());
        return NexmuxJson.write(Map.of("error", error));
    }

    /** fullCode → HTTP 状态（未知码 500）。 */
    public static int httpStatusOf(String fullCode) {
        for (NexmuxStatusCode code : NexmuxStatusCode.values()) {
            if (code.fullCode().equals(fullCode)) {
                return code.httpStatusCode();
            }
        }
        return 500;
    }
}
