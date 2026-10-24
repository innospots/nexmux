package com.innospots.nexmux.core.shared.security;

import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private final CredentialCipher cipher = new CredentialCipher("test-secret");

    @Test
    void encryptDecryptRoundTrip() {
        String stored = cipher.encrypt("sk-upstream-key");

        assertThat(stored).startsWith(CredentialCipher.PREFIX).doesNotContain("sk-upstream-key");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-upstream-key");
    }

    @Test
    void encryptUsesRandomIv() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void decryptRejectsTamperedCiphertext() {
        String stored = cipher.encrypt("sk-upstream-key");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.code()).isEqualTo(NexmuxStatusCode.UPSTREAM_FAILURE.fullCode()));
    }

    @Test
    void decryptRejectsForeignKey() {
        String stored = cipher.encrypt("sk-upstream-key");

        assertThatThrownBy(() -> new CredentialCipher("other-secret").decrypt(stored))
                .isInstanceOf(NexusException.class);
    }

    @Test
    void legacyPlaintextPassesThrough() {
        // 存量明文行（无 aesgcm 前缀）兼容读取
        assertThat(cipher.decrypt("sk-legacy-plain")).isEqualTo("sk-legacy-plain");
    }

    @Test
    void disabledCipherPassesThroughBothWays() {
        CredentialCipher disabled = new CredentialCipher(null);

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.encrypt("sk-plain")).isEqualTo("sk-plain");
        assertThat(disabled.decrypt("sk-plain")).isEqualTo("sk-plain");
    }

    @Test
    void encryptedRowWithoutKeyFailsLoudly() {
        // 已加密数据在密钥缺失时拒绝读取（不得把密文当密钥发上游）
        String stored = cipher.encrypt("sk-upstream-key");

        assertThatThrownBy(() -> new CredentialCipher(null).decrypt(stored))
                .isInstanceOfSatisfying(NexusException.class, e ->
                        assertThat(e.getMessage()).contains(CredentialCipher.ENV_KEY));
    }
}
