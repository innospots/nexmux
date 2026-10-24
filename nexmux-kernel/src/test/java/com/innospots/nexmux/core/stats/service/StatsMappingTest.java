package com.innospots.nexmux.core.stats.service;

import com.innospots.nexmux.core.stats.domain.vo.UsageRowVo;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsMappingTest {

    @Test
    void mapsStandardAmountIndependentlyFromCostAndBilling() {
        Map<String, Object> row = new HashMap<>();
        row.put("dim", "model-a");
        row.put("requests", 1L);
        row.put("standardUsd", new BigDecimal("0.0002"));
        row.put("billedUsd", new BigDecimal("0.0007"));
        row.put("costUsd", new BigDecimal("0.0005"));

        UsageRowVo result = StatsMapping.toUsageRow(row);

        assertThat(result.standardUsd()).isEqualByComparingTo("0.0002");
        assertThat(result.billedUsd()).isEqualByComparingTo("0.0007");
        assertThat(result.costUsd()).isEqualByComparingTo("0.0005");
    }
}
