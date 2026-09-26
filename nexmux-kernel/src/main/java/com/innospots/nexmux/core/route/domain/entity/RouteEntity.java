package com.innospots.nexmux.core.route.domain.entity;

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
@Table(name = RouteEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_route_alias", columnList = "alias", unique = true)
})
@TableName(RouteEntity.TABLE_NAME)
public class RouteEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_route";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String routeId;

    @Override
    public String idPrefix() {
        return "rt";
    }

    @Column(length = 64, nullable = false)
    private String alias;

    @Column(length = 128, nullable = false)
    private String displayName;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String routeSpec;

    @Column(columnDefinition = "CLOB")
    private String publishedRouteSpec;

    private LocalDateTime publishedAt;

    @Column(length = 64)
    private String publishedBy;
}
