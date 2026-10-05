-- Nexmux console configuration tables (P0)
CREATE TABLE IF NOT EXISTS mux_provider (
    provider_id VARCHAR(32) NOT NULL PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(16) NOT NULL,
    default_protocol VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    vendor_code VARCHAR(64) NOT NULL,
    source_vendor_code VARCHAR(64) NOT NULL,
    icon_uri VARCHAR(1024),
    base_url VARCHAR(1024) NOT NULL,
    region VARCHAR(64),
    timeout_ms INT,
    proxy VARCHAR(512),
    capability_extension CLOB,
    extension_config CLOB,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_provider_code UNIQUE (provider_code)
);

CREATE TABLE IF NOT EXISTS mux_provider_credential (
    credential_id VARCHAR(32) NOT NULL PRIMARY KEY,
    provider_id VARCHAR(32) NOT NULL,
    credential_mode VARCHAR(16) NOT NULL,
    ciphertext CLOB,
    secret_reference VARCHAR(512),
    key_version INT NOT NULL,
    rotated_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_provider_credential_provider UNIQUE (provider_id)
);

CREATE TABLE IF NOT EXISTS mux_model (
    model_id VARCHAR(32) NOT NULL PRIMARY KEY,
    model_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    icon_uri VARCHAR(1024),
    release_date DATE,
    context_length BIGINT,
    max_output_tokens BIGINT,
    input_modalities VARCHAR(256),
    output_modalities VARCHAR(256),
    capability_tags CLOB,
    tags CLOB,
    description VARCHAR(2000),
    metadata_spec CLOB,
    default_params CLOB,
    primary_provider_id VARCHAR(32),
    primary_provider_model_id VARCHAR(256),
    primary_upstream_protocol VARCHAR(32),
    primary_binding_weight INT,
    primary_cost_multiplier DECIMAL(10,4),
    primary_reference_multiplier DECIMAL(10,4),
    primary_binding_enabled BOOLEAN NOT NULL,
    extra_bindings CLOB NOT NULL,
    pricing_spec CLOB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_model_code UNIQUE (model_code)
);

CREATE TABLE IF NOT EXISTS mux_route (
    route_id VARCHAR(32) NOT NULL PRIMARY KEY,
    alias VARCHAR(64) NOT NULL,
    route_code VARCHAR(64),
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    route_spec CLOB NOT NULL,
    published_route_spec CLOB,
    published_at TIMESTAMP,
    published_by VARCHAR(64),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_route_alias UNIQUE (alias)
);

-- 存量库迁移（B2 增加 route_code 稳定业务键；空表直接生效，存量行以 alias 回填）
ALTER TABLE mux_route ADD COLUMN IF NOT EXISTS route_code VARCHAR(64);
UPDATE mux_route SET route_code = alias WHERE route_code IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_mux_route_code ON mux_route(route_code);

CREATE TABLE IF NOT EXISTS mux_client (
    client_id VARCHAR(32) NOT NULL PRIMARY KEY,
    client_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    app_key_hash VARCHAR(128) NOT NULL,
    app_key_salt VARCHAR(64) NOT NULL,
    app_key_version INT NOT NULL,
    authorized_route_ids CLOB NOT NULL,
    quota_spec CLOB,
    rate_limit_spec CLOB,
    source_template_id VARCHAR(32),
    tags CLOB,
    expires_at TIMESTAMP,
    app_key_rotated_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_client_code UNIQUE (client_code)
);

-- 存量库迁移（B4：客户端标签 / 有效期 / 密钥轮换时间）
ALTER TABLE mux_client ADD COLUMN IF NOT EXISTS tags CLOB;
ALTER TABLE mux_client ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE mux_client ADD COLUMN IF NOT EXISTS app_key_rotated_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS mux_client_template (
    template_id VARCHAR(32) NOT NULL PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    authorized_route_ids CLOB NOT NULL,
    quota_spec CLOB,
    rate_limit_spec CLOB,
    tags CLOB,
    description VARCHAR(2000),
    validity_type VARCHAR(16),
    validity_days INT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_client_template_code UNIQUE (template_code)
);

-- 存量库迁移（B4：模板描述 / 有效期规则）
ALTER TABLE mux_client_template ADD COLUMN IF NOT EXISTS description VARCHAR(2000);
ALTER TABLE mux_client_template ADD COLUMN IF NOT EXISTS validity_type VARCHAR(16);
ALTER TABLE mux_client_template ADD COLUMN IF NOT EXISTS validity_days INT;

CREATE TABLE IF NOT EXISTS mux_config_audit_log (
    audit_id VARCHAR(32) NOT NULL PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    change_summary VARCHAR(512),
    change_diff CLOB,
    operator_id VARCHAR(64),
    client_ip VARCHAR(64),
    request_id VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

-- 存量库迁移（B5：审计操作上下文）
ALTER TABLE mux_config_audit_log ADD COLUMN IF NOT EXISTS client_ip VARCHAR(64);
ALTER TABLE mux_config_audit_log ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE mux_config_audit_log ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);

CREATE TABLE IF NOT EXISTS mux_request_log (
    request_id VARCHAR(64) NOT NULL PRIMARY KEY,
    client_id VARCHAR(32),
    route_id VARCHAR(32),
    model_code VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    latency_ms BIGINT,
    error_code VARCHAR(64),
    route_trace CLOB,
    requested_at TIMESTAMP,
    app_key_id VARCHAR(64),
    user_email VARCHAR(128),
    provider_code VARCHAR(64),
    protocol VARCHAR(64),
    route_group VARCHAR(64),
    application VARCHAR(128),
    alias VARCHAR(64),
    http_status INT,
    input_tokens BIGINT,
    output_tokens BIGINT,
    cache_read_tokens BIGINT,
    cache_write_tokens BIGINT,
    cost_usd DECIMAL(18,8),
    billing_usd DECIMAL(18,8),
    ttft_ms BIGINT,
    upstream_model VARCHAR(256),
    upstream_endpoint VARCHAR(1024),
    upstream_status VARCHAR(16),
    upstream_latency_ms BIGINT,
    retries INT,
    client_ip VARCHAR(64),
    ingress_method VARCHAR(16),
    ingress_path VARCHAR(512),
    trace_id VARCHAR(64),
    error_type VARCHAR(64),
    pricing_snapshot CLOB,
    prompt_snapshot CLOB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

-- 存量库迁移（B5：日志明细字段；聚合查询索引）
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS app_key_id VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS user_email VARCHAR(128);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS provider_code VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS protocol VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS route_group VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS application VARCHAR(128);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS alias VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS http_status INT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS input_tokens BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS output_tokens BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS cache_read_tokens BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS cache_write_tokens BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS cost_usd DECIMAL(18,8);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS billing_usd DECIMAL(18,8);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS ttft_ms BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS upstream_model VARCHAR(256);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS upstream_endpoint VARCHAR(1024);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS upstream_status VARCHAR(16);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS upstream_latency_ms BIGINT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS retries INT;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS client_ip VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS ingress_method VARCHAR(16);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS ingress_path VARCHAR(512);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS error_type VARCHAR(64);
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS pricing_snapshot CLOB;
ALTER TABLE mux_request_log ADD COLUMN IF NOT EXISTS prompt_snapshot CLOB;
CREATE INDEX IF NOT EXISTS idx_mux_request_log_time ON mux_request_log(requested_at);
CREATE INDEX IF NOT EXISTS idx_mux_request_log_model_time ON mux_request_log(model_code, requested_at);
CREATE INDEX IF NOT EXISTS idx_mux_request_log_provider_time ON mux_request_log(provider_code, requested_at);
CREATE INDEX IF NOT EXISTS idx_mux_request_log_client_time ON mux_request_log(client_id, requested_at);
