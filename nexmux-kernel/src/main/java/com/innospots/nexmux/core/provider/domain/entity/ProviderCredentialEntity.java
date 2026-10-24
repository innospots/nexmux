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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = ProviderCredentialEntity.TABLE_NAME, indexes = {
        @Index(name = "uk_mux_provider_credential_provider", columnList = "provider_id", unique = true)
})
@TableName(ProviderCredentialEntity.TABLE_NAME)
public class ProviderCredentialEntity extends BaseEntity {

    public static final String TABLE_NAME = "mux_provider_credential";

    @TableId(type = IdType.ASSIGN_UUID)
    @Id
    @Column(length = 32, nullable = false)
    private String credentialId;

    @Override
    public String idPrefix() {
        return "pc";
    }

    @Column(length = 32, nullable = false)
    private String providerId;

    @Column(length = 16, nullable = false)
    private String credentialMode;

    @Column(columnDefinition = "CLOB")
    private String ciphertext;

    @Column(length = 512)
    private String secretReference;

    @Column(nullable = false)
    private Integer keyVersion;

    private LocalDateTime rotatedAt;
}
