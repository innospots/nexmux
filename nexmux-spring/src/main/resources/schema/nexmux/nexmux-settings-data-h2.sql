-- ---------------------------------------------------------------------------
-- Nexmux 服务设置种子数据（H2）
--
-- 与服务端 com.innospots.nexmux.core.settings.service.PlatformSettingService#DEFAULTS
-- 保持一致的 14 个键（顺序即服务设置页展示顺序）。
-- 取值来源：本地开发库 mux_platform_setting 全表导出（2026-10-12），与 DEFAULTS 相同。
--
-- 幂等约定（与 nexmux-catalog-data-h2.sql 一致）：
--   MERGE ... WHEN NOT MATCHED THEN INSERT —— 只插不更新；
--   页面/接口对该值的修改重启后保留；
--   因此修改本文件的值对已落库的行不生效，需清库或手工迁移。
--
-- ⚠️ 生产评审点：debugInvokeEnabled=true 表示调试台可发起真实上游调用（产生费用）。
--    若生产需要收紧，改成 'false'，或在服务设置页调整。
-- ---------------------------------------------------------------------------

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'timezone', 'Asia/Shanghai', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'language', 'zh-CN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'displayName', 'GateWeave Model Console', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'currency', 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'currencyDecimals', '4', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'tokenDisplay', 'compact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'dateFormat', 'YYYY-MM-DD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'timeFormat', 'HH:mm:ss', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'promptPolicy', 'masked', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'debugInvokeEnabled', 'true', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'debugDailyQuota', '200', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'logRetentionDays', '90', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'requestTimeoutMs', '60000', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);

MERGE INTO mux_platform_setting AS t USING (VALUES (
    'upstreamConnectTimeoutMs', '10000', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'
)) AS s(setting_key, setting_value, created_at, updated_at, created_by, updated_by)
ON t.setting_key = s.setting_key
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, created_at, updated_at, created_by, updated_by)
    VALUES (s.setting_key, s.setting_value, s.created_at, s.updated_at, s.created_by, s.updated_by);
