package com.innospots.nexmux.core.shared.security;

import com.innospots.nexmux.core.shared.status.NexmuxStatusCode;
import com.innospots.nexus.base.exception.NexusException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provider 上游凭证落库加密（AES-GCM，128 位认证标签）。密钥取自系统属性
 * {@value #PROP_KEY} 或环境变量 {@value #ENV_KEY}（属性优先），经 SHA-256 派生
 * 256 位 AES 密钥；轮换密钥即更换该配置并重启，存量密文需先以旧密钥轮换凭证。
 *
 * <p>密文格式 {@code aesgcm:v1:base64(iv‖密文)}；无前缀的存量行按明文兼容读取
 * （下次保存 / 轮换时自动升级为密文）。未配置密钥时不启用加密（{@link #encrypt}
 * 原样透传明文，维持旧行为），装配方启动时应告警；已加密数据在密钥缺失时读取
 * 抛 {@link NexusException}，绝不把密文当密钥发给上游。
 */
public final class CredentialCipher {

    /** 环境变量密钥名。 */
    public static final String ENV_KEY = "NEXMUX_CREDENTIAL_SECRET";

    /** 系统属性密钥名（优先于环境变量）。 */
    public static final String PROP_KEY = "nexmux.credential-secret";

    static final String PREFIX = "aesgcm:v1:";

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    /**
     * @param secret 密钥原文；null / blank = 不启用加密（encrypt 原样透传）
     */
    public CredentialCipher(String secret) {
        this.key = secret == null || secret.isBlank()
                ? null : new SecretKeySpec(sha256(secret.trim()), "AES");
    }

    /** 从运行环境装配：系统属性 {@value #PROP_KEY} 优先，其次环境变量 {@value #ENV_KEY}。 */
    public static CredentialCipher fromEnvironment() {
        String secret = System.getProperty(PROP_KEY);
        if (secret == null || secret.isBlank()) {
            secret = System.getenv(ENV_KEY);
        }
        return new CredentialCipher(secret);
    }

    /** 是否已配置密钥（装配方启动告警用）。 */
    public boolean enabled() {
        return key != null;
    }

    /** 加密落库（未启用时原样返回明文）。 */
    public String encrypt(String plain) {
        if (key == null || plain == null) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(IV_BYTES + encrypted.length)
                    .put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                    "credential encryption failed: " + e.getMessage());
        }
    }

    /** 解密读取：带前缀 → 解密；无前缀 → 存量明文原样返回。 */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        if (key == null) {
            throw NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                    "credential is encrypted but " + ENV_KEY + " is not configured");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw NexusException.build(NexmuxStatusCode.UPSTREAM_FAILURE,
                    "credential decryption failed: " + e.getMessage());
        }
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
