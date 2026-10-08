package com.innospots.nexmux.console.audit;

import com.innospots.nexmux.core.audit.domain.vo.AuditLogVo;
import com.innospots.nexmux.console.shared.LogTexts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Audit UI 投影（/api/d/audit-logs 契约的装配点）：change_diff 信封解析、
 * before/after 快照 pretty 化与行级 diff（LCS）、操作/资源类型展示名映射。
 */
public final class AuditViewMapper {

    /** 操作码 → 展示名（后端存储大写操作码）。 */
    private static final Map<String, String> OPERATION_LABELS = Map.of(
            "CREATE", "创建",
            "UPDATE", "修改",
            "DELETE", "删除",
            "ENABLE", "启用",
            "DISABLE", "停用",
            "PUBLISH", "发布",
            "ROTATE", "Rotate Secret");

    private static final Map<String, String> RESOURCE_TYPE_LABELS = Map.of(
            "PROVIDER", "Provider",
            "MODEL", "Model",
            "ROUTE", "Route",
            "CLIENT", "API Client",
            "CLIENT_TEMPLATE", "Client Template");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AuditViewMapper() {
    }

    /** VO → 展示记录（列表与详情同一整形）。 */
    public static AuditRecord toRecord(AuditLogVo vo) {
        Snapshots snapshots = splitSnapshots(vo.changeDiff());
        String beforeJson = snapshots.before() == null ? null : LogTexts.prettyJson(snapshots.before().toString());
        String afterJson = snapshots.after() == null ? null : LogTexts.prettyJson(snapshots.after().toString());
        String operation = vo.operation() == null ? "" : vo.operation();
        String resourceType = vo.resourceType() == null ? "" : vo.resourceType();
        LocalDateTime createdAt = vo.createdAt();

        return new AuditRecord(
                vo.auditId(),
                createdAt == null ? "" : DATE_FORMAT.format(createdAt),
                LogTexts.fullTime(createdAt) + " (UTC+8)",
                LogTexts.minuteTime(createdAt),
                vo.operatorId() == null ? "—" : vo.operatorId(),
                OPERATION_LABELS.getOrDefault(operation, operation),
                operation.toLowerCase(),
                RESOURCE_TYPE_LABELS.getOrDefault(resourceType, resourceType),
                resourceType.toLowerCase(),
                vo.resourceId() == null ? "—" : vo.resourceId(),
                vo.clientIp() == null ? "—" : vo.clientIp(),
                vo.requestId() == null ? "—" : vo.requestId(),
                vo.userAgent() == null ? "—" : vo.userAgent(),
                beforeJson,
                afterJson,
                diffLines(beforeJson, afterJson),
                List.of(
                        new AuditRecord.ContextRow("操作 IP", vo.clientIp() == null ? "—" : vo.clientIp()),
                        new AuditRecord.ContextRow("请求 ID", vo.requestId() == null ? "—" : vo.requestId()),
                        new AuditRecord.ContextRow("终端", vo.userAgent() == null ? "—" : vo.userAgent()),
                        new AuditRecord.ContextRow("变更摘要", vo.changeSummary() == null ? "—" : vo.changeSummary())));
    }

    /** 详情 VO → 展示记录（字段与概要同构，显式转换）。 */
    public static AuditRecord toRecord(com.innospots.nexmux.core.audit.domain.vo.AuditLogDetailVo d) {
        return toRecord(new AuditLogVo(
                d.auditId(), d.resourceType(), d.resourceId(), d.operation(), d.changeSummary(),
                d.changeDiff(), d.operatorId(), d.clientIp(), d.requestId(), d.userAgent(), d.createdAt()));
    }

    /** change_diff 信封解析：{before, after} 或旧单快照（视为 after）。 */
    private static Snapshots splitSnapshots(String changeDiff) {
        JsonNode parsed = LogTexts.readJson(changeDiff);
        if (parsed != null && parsed.isObject() && (parsed.has("before") || parsed.has("after"))) {
            JsonNode before = parsed.get("before");
            JsonNode after = parsed.get("after");
            return new Snapshots(
                    before == null || before.isNull() ? null : before,
                    after == null || after.isNull() ? null : after);
        }
        return new Snapshots(null, parsed);
    }

    private record Snapshots(JsonNode before, JsonNode after) {
    }

    /** 行级 diff（LCS；审计 JSON 规模小，O(m*n) 可接受）。 */
    static List<AuditRecord.DiffLine> diffLines(String before, String after) {
        String[] a = before == null ? new String[0] : before.split("\n", -1);
        String[] b = after == null ? new String[0] : after.split("\n", -1);
        int m = a.length;
        int n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                dp[i][j] = a[i].equals(b[j]) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<AuditRecord.DiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (a[i].equals(b[j])) {
                lines.add(new AuditRecord.DiffLine("same", a[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                lines.add(new AuditRecord.DiffLine("remove", a[i]));
                i++;
            } else {
                lines.add(new AuditRecord.DiffLine("add", b[j]));
                j++;
            }
        }
        while (i < m) {
            lines.add(new AuditRecord.DiffLine("remove", a[i++]));
        }
        while (j < n) {
            lines.add(new AuditRecord.DiffLine("add", b[j++]));
        }
        return lines;
    }
}
