package com.innospots.nexmux.inbound.protocol;

import java.io.IOException;

/**
 * 上游 HTTP 错误（状态 ≥400）：携带上游状态码与（截断后的）错误体。
 *
 * <p>语义分层：4xx 为接入方输入类错误（如上下文超长、参数非法），网关应**透传**
 * 状态与错误体，让调用方能区分「自己的错」与「网关/上游故障」；5xx 为上游故障，
 * 由网关统一归约为 502。
 */
public final class UpstreamCallException extends IOException {

    private final int status;
    private final String responseBody;

    public UpstreamCallException(int status, String responseBody) {
        super("HTTP " + status + ": " + responseBody);
        this.status = status;
        this.responseBody = responseBody;
    }

    /** 上游返回的 HTTP 状态码。 */
    public int status() {
        return status;
    }

    /** 上游错误体（适配器已截断至 200 字符）。 */
    public String responseBody() {
        return responseBody;
    }

    /** 是否接入方输入类错误（4xx，应透传而非归约 502）。 */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }
}
