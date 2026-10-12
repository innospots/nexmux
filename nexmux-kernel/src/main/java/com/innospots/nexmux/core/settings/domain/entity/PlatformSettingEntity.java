package com.innospots.nexmux.core.settings.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.innospots.nexus.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = PlatformSettingEntity.TABLE_NAME)
@TableName(PlatformSettingEntity.TABLE_NAME)
public class PlatformSettingEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_platform_setting";

    @TableId(type = IdType.INPUT)
    @Id
    @Column(length = 64, nullable = false)
    private String settingKey;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String settingValue;

    @Override
    public String idPrefix() {
        return "pst";
    }
}
