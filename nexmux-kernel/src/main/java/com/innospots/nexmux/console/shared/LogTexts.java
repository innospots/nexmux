package com.innospots.nexmux.console.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innospots.nexmux.core.shared.json.NexmuxJson;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 日志 / 成本 / 审计投影共用格式化工具（与原 BFF format-shared 逐字对齐）。
 */
public final class LogTexts {

    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final ObjectMapper PRETTY = new ObjectMapper();

    private LogTexts() {
    }

    /** "yyyy-MM-dd HH:mm:ss"（空为 ""）。 */
    public static String fullTime(LocalDateTime time) {
        return time == null ? "" : FULL.format(time);
    }

    /** "MM-dd HH:mm:ss"（空为 ""）。 */
    public static String shortTime(LocalDateTime time) {
        return time == null ? "" : SHORT.format(time);
    }

    /** "MM-dd HH:mm"（空为 ""）。 */
    public static String minuteTime(LocalDateTime time) {
        return time == null ? "" : MINUTE.format(time);
    }

    /** 千分位（en-US）。 */
    public static String thousands(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    /** token 缩略（1234 → 1.2K，2500000 → 2.5M）。 */
    public static String compactTokens(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 1000) {
            return String.format(Locale.US, "%.1fK", n / 1000.0);
        }
        return String.valueOf(n);
    }

    /** CLOB JSON 容错解析（空 / 非法 → null）。 */
    public static JsonNode readJson(String json) {
        try {
            return NexmuxJson.read(json, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** pretty JSON（2 空格缩进；非法原文返回；空为 null）。 */
    public static String prettyJson(String text) {
        if (text == null) {
            return null;
        }
        JsonNode node = readJson(text);
        if (node == null) {
            return text;
        }
        try {
            return PRETTY.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return text;
        }
    }
}
