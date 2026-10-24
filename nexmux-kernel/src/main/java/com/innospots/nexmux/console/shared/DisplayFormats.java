package com.innospots.nexmux.console.shared;

import com.innospots.nexmux.core.settings.service.PlatformSettingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 展示格式快照（服务设置的渲染侧投影）：时区、日期/时间格式、币种符号、
 * 金额小数位。由 {@link #from(Map)} 从设置键值解析，非法值回退默认。
 *
 * 时间语义：DB 存储为服务器本地时间（AuditMetaObjectHandler 用
 * LocalDateTime.now()），渲染时「系统默认时区 → 配置时区」换算；
 * 配置与服务器一致时零换算。
 */
public record DisplayFormats(
        ZoneId zone,
        String zoneLabel,
        DateTimeFormatter fullFormatter,
        DateTimeFormatter shortFormatter,
        DateTimeFormatter minuteFormatter,
        DateTimeFormatter dayMinuteFormatter,
        DateTimeFormatter dateFormatter,
        DateTimeFormatter shortDateFormatter,
        String currencySymbol,
        String currencyCode,
        int decimals,
        String tokenDisplay) {

    /** 默认值（与 PlatformSettingService.DEFAULTS 对齐）。 */
    public static final DisplayFormats DEFAULTS = from(Map.of());

    /** 从设置键值解析（缺失 / 非法键回退默认）。 */
    public static DisplayFormats from(Map<String, String> values) {
        ZoneId zone = zoneOf(values.get("timezone"));
        String datePattern = switch (values.getOrDefault("dateFormat", "YYYY-MM-DD")) {
            case "DD/MM/YYYY" -> "dd/MM/yyyy";
            case "MM/DD/YYYY" -> "MM/dd/yyyy";
            default -> "yyyy-MM-dd";
        };
        boolean twelveHour = "hh:mm:ss A".equals(values.get("timeFormat"));
        String timePattern = twelveHour ? "hh:mm:ss a" : "HH:mm:ss";
        String minuteTimePattern = twelveHour ? "hh:mm a" : "HH:mm";
        String currencyCode = values.getOrDefault("currency", "USD");
        return new DisplayFormats(
                zone,
                zoneLabel(zone),
                formatter(datePattern + " " + timePattern),
                formatter("MM-dd " + timePattern),
                formatter("MM-dd " + minuteTimePattern),
                formatter(datePattern + " " + minuteTimePattern),
                formatter(datePattern),
                formatter("MM-dd"),
                switch (currencyCode) {
                    case "USD" -> "$";
                    case "CNY" -> "¥";
                    case "EUR" -> "€";
                    default -> currencyCode + " ";
                },
                currencyCode,
                decimalsOf(values.get("currencyDecimals")),
                values.getOrDefault("tokenDisplay", "compact"));
    }

    /** "yyyy-MM-dd HH:mm:ss" 量级（按配置格式与时区；空为 ""）。 */
    public String fullTime(LocalDateTime time) {
        return time == null ? "" : fullFormatter.format(shift(time));
    }

    /** "MM-dd HH:mm:ss" 量级（空为 ""）。 */
    public String shortTime(LocalDateTime time) {
        return time == null ? "" : shortFormatter.format(shift(time));
    }

    /** "MM-dd HH:mm" 量级（空为 ""）。 */
    public String minuteTime(LocalDateTime time) {
        return time == null ? "" : minuteFormatter.format(shift(time));
    }

    /** "yyyy-MM-dd HH:mm" 量级（空为 ""）。 */
    public String dayMinuteTime(LocalDateTime time) {
        return time == null ? "" : dayMinuteFormatter.format(shift(time));
    }

    /** 日期部分（空为 ""）。 */
    public String date(LocalDateTime time) {
        return time == null ? "" : dateFormatter.format(shift(time));
    }

    /** "MM-dd" 短日期（空为 ""）。 */
    public String shortDate(LocalDateTime time) {
        return time == null ? "" : shortDateFormatter.format(shift(time));
    }

    /** 金额：币种符号 + 配置小数位（空为 "—" 由调用方处理）。 */
    public String money(BigDecimal value) {
        return currencySymbol + value.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    /** 存储时间（服务器本地）→ 配置时区；一致时原样返回。 */
    private LocalDateTime shift(LocalDateTime time) {
        ZoneId system = ZoneId.systemDefault();
        return zone.equals(system)
                ? time
                : time.atZone(system).withZoneSameInstant(zone).toLocalDateTime();
    }

    /** 12 小时制的 AM/PM 标记固定 en-US（避免随 JVM locale 本地化为“下午”）。 */
    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, java.util.Locale.US);
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    /** "UTC+8" / "UTC-5" / "UTC+5:30" / 零偏移 "UTC"。 */
    private static String zoneLabel(ZoneId zone) {
        ZoneOffset offset = zone.getRules().getOffset(Instant.now());
        int totalSeconds = offset.getTotalSeconds();
        if (totalSeconds == 0) {
            return "UTC";
        }
        String sign = totalSeconds < 0 ? "-" : "+";
        int abs = Math.abs(totalSeconds);
        int hours = abs / 3600;
        int minutes = (abs % 3600) / 60;
        return minutes == 0
                ? "UTC" + sign + hours
                : "UTC" + sign + hours + ":" + String.format("%02d", minutes);
    }

    private static int decimalsOf(String value) {
        if (value == null || value.isBlank()) {
            return 4;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(0, Math.min(8, parsed));
        } catch (NumberFormatException e) {
            return 4;
        }
    }
}
