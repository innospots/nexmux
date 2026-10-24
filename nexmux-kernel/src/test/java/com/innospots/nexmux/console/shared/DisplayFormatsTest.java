package com.innospots.nexmux.console.shared;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayFormatsTest {

    @Test
    void defaultsMatchPlatformSettingDefaults() {
        DisplayFormats formats = DisplayFormats.from(Map.of());

        assertThat(formats.currencySymbol()).isEqualTo("$");
        assertThat(formats.decimals()).isEqualTo(4);
        assertThat(formats.tokenDisplay()).isEqualTo("compact");
        assertThat(formats.zone()).isEqualTo(ZoneId.systemDefault());
        assertThat(formats.money(new BigDecimal("30"))).isEqualTo("$30.0000");
    }

    @Test
    void parsesCurrencyDecimalsAndFormats() {
        DisplayFormats formats = DisplayFormats.from(Map.of(
                "currency", "CNY",
                "currencyDecimals", "2",
                "dateFormat", "DD/MM/YYYY",
                "timeFormat", "hh:mm:ss A"));

        assertThat(formats.currencySymbol()).isEqualTo("¥");
        assertThat(formats.money(new BigDecimal("30"))).isEqualTo("¥30.00");
        LocalDateTime t = LocalDateTime.of(2026, 10, 5, 15, 4, 9);
        assertThat(formats.date(t)).isEqualTo("05/10/2026");
        assertThat(formats.fullTime(t)).isEqualTo("05/10/2026 03:04:09 PM");
        assertThat(formats.shortTime(t)).isEqualTo("10-05 03:04:09 PM");
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        DisplayFormats formats = DisplayFormats.from(Map.of(
                "timezone", "Not/AZone",
                "currencyDecimals", "abc"));

        assertThat(formats.zone()).isEqualTo(ZoneId.systemDefault());
        assertThat(formats.decimals()).isEqualTo(4);
    }

    @Test
    void zoneShiftAppliesOnlyWhenZoneDiffers() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 5, 8, 0);
        ZoneId system = ZoneId.systemDefault();
        ZoneId other = system.equals(ZoneId.of("Asia/Tokyo"))
                ? ZoneId.of("Asia/Shanghai") : ZoneId.of("Asia/Tokyo");

        DisplayFormats same = DisplayFormats.from(Map.of("timezone", system.getId()));
        assertThat(same.fullTime(t)).isEqualTo("2026-10-05 08:00:00");

        DisplayFormats shifted = DisplayFormats.from(Map.of("timezone", other.getId()));
        assertThat(shifted.fullTime(t)).isNotEqualTo("2026-10-05 08:00:00");
        assertThat(shifted.zoneLabel()).startsWith("UTC");
    }
}
