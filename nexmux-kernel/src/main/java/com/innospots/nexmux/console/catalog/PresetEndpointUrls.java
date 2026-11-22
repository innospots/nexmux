package com.innospots.nexmux.console.catalog;

import java.util.Locale;

/**
 * 上游协议 → 默认端点路径推导（模板目录展示用）。
 *
 * <p>服务基址含占位符（如 {@code https://{resource}.openai.azure.com}）时不推导端点，
 * 由使用者导入后自行补全。</p>
 */
public final class PresetEndpointUrls {

    private PresetEndpointUrls() {
    }

    /**
     * 按协议推导对话端点。
     *
     * @param baseUrl  服务基址
     * @param protocol 上游协议
     * @return 对话端点；无法推导时返回 {@code null}
     */
    public static String chatUrl(String baseUrl, String protocol) {
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.indexOf('{') >= 0) {
            return null;
        }
        String suffix = suffixOf(protocol);
        if (suffix == null) {
            return null;
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        return base + suffix;
    }

    private static String suffixOf(String protocol) {
        if (protocol == null) {
            return null;
        }
        return switch (protocol.trim().toLowerCase(Locale.ROOT)) {
            case "openai-completions", "mistral-conversations" -> "/chat/completions";
            case "openai-responses", "openai-codex-responses", "azure-openai-responses" -> "/responses";
            case "anthropic-messages", "pi-messages" -> "/v1/messages";
            case "google-generative-ai" -> "/v1beta/models";
            case "google-vertex" -> "/v1/projects";
            default -> null;
        };
    }
}
