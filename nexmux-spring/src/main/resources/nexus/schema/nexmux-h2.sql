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
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_client_code UNIQUE (client_code)
);

CREATE TABLE IF NOT EXISTS mux_client_template (
    template_id VARCHAR(32) NOT NULL PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    authorized_route_ids CLOB NOT NULL,
    quota_spec CLOB,
    rate_limit_spec CLOB,
    tags CLOB,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uk_mux_client_template_code UNIQUE (template_code)
);

CREATE TABLE IF NOT EXISTS mux_config_audit_log (
    audit_id VARCHAR(32) NOT NULL PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    change_summary VARCHAR(512),
    change_diff CLOB,
    operator_id VARCHAR(64),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

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
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);
