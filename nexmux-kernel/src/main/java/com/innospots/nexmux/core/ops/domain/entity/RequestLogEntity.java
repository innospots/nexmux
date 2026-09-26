package com.innospots.nexmux.core.ops.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = RequestLogEntity.TABLE_NAME)
@TableName(RequestLogEntity.TABLE_NAME)
public class RequestLogEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_request_log";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 64, nullable = false)
    private String requestId;

    @Override
    public String idPrefix() {
        return "rq";
    }

    @Column(length = 32)
    private String clientId;

    @Column(length = 32)
    private String routeId;

    @Column(length = 64)
    private String modelCode;

    @Column(length = 16, nullable = false)
    private String status;

    private Long latencyMs;

    @Column(length = 64)
    private String errorCode;

    @Column(columnDefinition = "CLOB")
    private String routeTrace;

    private LocalDateTime requestedAt;
}
