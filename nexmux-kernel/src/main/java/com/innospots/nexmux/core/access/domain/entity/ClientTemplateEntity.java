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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = ClientTemplateEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_client_template_code", columnList = "template_code", unique = true)
})
@TableName(ClientTemplateEntity.TABLE_NAME)
public class ClientTemplateEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_client_template";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String templateId;

    @Override
    public String idPrefix() {
        return "ct";
    }

    @Column(length = 64, nullable = false)
    private String templateCode;

    @Column(length = 128, nullable = false)
    private String displayName;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String authorizedRouteIds;

    @Column(columnDefinition = "CLOB")
    private String quotaSpec;

    @Column(columnDefinition = "CLOB")
    private String rateLimitSpec;

    @Column(columnDefinition = "CLOB")
    private String tags;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;
}
