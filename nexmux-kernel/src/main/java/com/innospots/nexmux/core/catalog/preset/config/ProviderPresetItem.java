package com.innospots.nexmux.core.catalog.preset.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.innospots.nexus.base.i18n.I18nObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Classpath {@code provider-presets.yaml} 中的供应商预设。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ProviderPresetItem", description = "供应商预设条目")
public class ProviderPresetItem {

    private String providerCode;
    private I18nObject displayName;
    private String providerType;
    private String defaultProtocol;
    private String vendorCode;
    private String sourceVendorCode;
    private String baseUrl;
    private Integer timeoutMs;
    private I18nObject description;
    private Integer modelCount;

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public I18nObject getDisplayName() {
        return displayName;
    }

    public void setDisplayName(I18nObject displayName) {
        this.displayName = displayName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getDefaultProtocol() {
        return defaultProtocol;
    }

    public void setDefaultProtocol(String defaultProtocol) {
        this.defaultProtocol = defaultProtocol;
    }

    public String getVendorCode() {
        return vendorCode;
    }

    public void setVendorCode(String vendorCode) {
        this.vendorCode = vendorCode;
    }

    public String getSourceVendorCode() {
        return sourceVendorCode;
    }

    public void setSourceVendorCode(String sourceVendorCode) {
        this.sourceVendorCode = sourceVendorCode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public I18nObject getDescription() {
        return description;
    }

    public void setDescription(I18nObject description) {
        this.description = description;
    }

    public Integer getModelCount() {
        return modelCount;
    }

    public void setModelCount(Integer modelCount) {
        this.modelCount = modelCount;
    }
}