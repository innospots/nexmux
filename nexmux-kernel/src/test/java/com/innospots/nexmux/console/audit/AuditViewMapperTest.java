package com.innospots.nexmux.console.audit;

import com.innospots.nexmux.console.shared.DisplayFormatsProvider;
import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditViewMapper} 单测：change_diff 信封解析、行级 diff（LCS）、展示名映射。
 */
class AuditViewMapperTest {

    private final AuditViewMapper mapper = new AuditViewMapper(DisplayFormatsProvider.defaults());

    @Test
    void splitsEnvelopeAndComputesDiff() {
        AuditLogVo vo = new AuditLogVo(
                "a1", "PROVIDER", "zai", "UPDATE", "改了 baseUrl",
                "{\"before\":{\"baseUrl\":\"https://a\"},\"after\":{\"baseUrl\":\"https://b\"}}",
                "admin", "1.2.3.4", "req-1", "curl/8",
                LocalDateTime.of(2026, 10, 5, 8, 0));

        AuditRecord record = mapper.toRecord(vo);

        assertThat(record.operation()).isEqualTo("修改");
        assertThat(record.operationType()).isEqualTo("update");
        assertThat(record.resourceType()).isEqualTo("Provider");
        assertThat(record.date()).isEqualTo("2026-10-05");
        assertThat(record.timeUtc()).isEqualTo("2026-10-05 08:00:00 (UTC+8)");
        assertThat(record.timeShort()).isEqualTo("10-05 08:00");
        assertThat(record.beforeJson()).contains("https://a");
        assertThat(record.afterJson()).contains("https://b");
        assertThat(record.diffLines()).anySatisfy(line -> {
            assertThat(line.type()).isEqualTo("remove");
            assertThat(line.text()).contains("https://a");
        });
        assertThat(record.diffLines()).anySatisfy(line -> {
            assertThat(line.type()).isEqualTo("add");
            assertThat(line.text()).contains("https://b");
        });
        assertThat(record.context()).extracting(AuditRecord.ContextRow::label)
                .containsExactly("操作 IP", "请求 ID", "终端", "变更摘要");
    }

    @Test
    void treatsLegacySingleSnapshotAsAfter() {
        AuditLogVo vo = new AuditLogVo(
                "a2", "ROUTE", "general", "CREATE", null,
                "{\"alias\":\"general\"}", "admin", null, null, null,
                LocalDateTime.of(2026, 10, 1, 12, 0));

        AuditRecord record = mapper.toRecord(vo);

        assertThat(record.beforeJson()).isNull();
        assertThat(record.afterJson()).contains("general");
        assertThat(record.diffLines()).allSatisfy(line -> assertThat(line.type()).isEqualTo("add"));
        assertThat(record.operation()).isEqualTo("创建");
        assertThat(record.resourceType()).isEqualTo("Route");
        assertThat(record.ip()).isEqualTo("—");
    }

    @Test
    void diffKeepsSameLines() {
        List<AuditRecord.DiffLine> lines = AuditViewMapper.diffLines("x\ny", "x\nz");

        assertThat(lines).containsExactly(
                new AuditRecord.DiffLine("same", "x"),
                new AuditRecord.DiffLine("remove", "y"),
                new AuditRecord.DiffLine("add", "z"));
    }
}
