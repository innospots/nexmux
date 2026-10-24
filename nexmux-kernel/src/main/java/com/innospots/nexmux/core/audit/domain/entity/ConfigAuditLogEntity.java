package com.innospots.nexmux.core.audit.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = ConfigAuditLogEntity.TABLE_NAME)
@TableName(ConfigAuditLogEntity.TABLE_NAME)
public class ConfigAuditLogEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_config_audit_log";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String auditId;

    @Override
    public String idPrefix() {
        return "al";
    }

    @Column(length = 64, nullable = false)
    private String resourceType;

    @Column(length = 64, nullable = false)
    private String resourceId;

    @Column(length = 32, nullable = false)
    private String operation;

    @Column(length = 512)
    private String changeSummary;

    @Column(columnDefinition = "CLOB")
    private String changeDiff;

    @Column(length = 64)
    private String operatorId;

    @Column(length = 64)
    private String clientIp;

    @Column(length = 64)
    private String requestId;

    @Column(length = 512)
    private String userAgent;
}
