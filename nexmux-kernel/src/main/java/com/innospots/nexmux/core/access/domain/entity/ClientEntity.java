package com.innospots.nexmux.core.access.domain.entity;

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
@Table(name = ClientEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_client_code", columnList = "client_code", unique = true)
})
@TableName(ClientEntity.TABLE_NAME)
public class ClientEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_client";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String clientId;

    @Override
    public String idPrefix() {
        return "cl";
    }

    @Column(length = 64, nullable = false)
    private String clientCode;

    @Column(length = 128, nullable = false)
    private String displayName;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(length = 128, nullable = false)
    private String appKeyHash;

    @Column(length = 64, nullable = false)
    private String appKeySalt;

    @Column(nullable = false)
    private Integer appKeyVersion;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String authorizedRouteIds;

    @Column(columnDefinition = "CLOB")
    private String quotaSpec;

    @Column(columnDefinition = "CLOB")
    private String rateLimitSpec;

    @Column(length = 32)
    private String sourceTemplateId;

    @Column(columnDefinition = "CLOB")
    private String tags;

    private java.time.LocalDateTime expiresAt;

    private Long configRevision;

    private java.time.LocalDateTime appKeyRotatedAt;
}
