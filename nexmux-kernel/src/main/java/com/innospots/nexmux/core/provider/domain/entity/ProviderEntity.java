package com.innospots.nexmux.core.provider.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = ProviderEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_provider_code", columnList = "provider_code", unique = true)
})
@TableName(ProviderEntity.TABLE_NAME)
public class ProviderEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_provider";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String providerId;

    @Override
    public String idPrefix() {
        return "pv";
    }

    @Column(length = 64, nullable = false)
    private String providerCode;

    @Column(length = 128, nullable = false)
    private String displayName;

    @Column(length = 16, nullable = false)
    private String providerType;

    @Column(length = 32, nullable = false)
    private String defaultProtocol;

    @Column(length = 500)
    private String description;

    @Column(length = 64, nullable = false)
    private String vendorCode;

    @Column(length = 64, nullable = false)
    private String sourceVendorCode;

    @Column(length = 1024)
    private String iconUri;

    @Column(length = 1024, nullable = false)
    private String baseUrl;

    @Column(length = 64)
    private String region;

    private Integer timeoutMs;

    @Column(length = 512)
    private String proxy;

    @Column(columnDefinition = "CLOB")
    private String capabilityExtension;

    @Column(columnDefinition = "CLOB")
    private String extensionConfig;

    @Column(length = 16, nullable = false)
    private String status;
}
