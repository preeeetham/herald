package dev.herald.core.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts small secrets (endpoint signing keys) at rest with AES-256-GCM.
 *
 * <p>Layout: {@code [version:1][iv:12][ciphertext+tag]}. Associated data binds a ciphertext to its
 * owner (for example the tenant id) so a row copied to another tenant fails to decrypt.
 */
public final class SecretCipher {

    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(byte[] keyBytes) {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 requires a 32-byte key");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public static SecretCipher fromBase64(String base64Key) {
        return new SecretCipher(Base64.getDecoder().decode(base64Key));
    }

    public byte[] encrypt(String plaintext, String associatedData) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(byte[] ciphertext, String associatedData) {
        if (ciphertext.length < 1 + IV_BYTES + TAG_BITS / 8 || ciphertext[0] != VERSION) {
            throw new IllegalArgumentException("Unsupported ciphertext format");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ciphertext, 1, IV_BYTES));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(ciphertext, 1 + IV_BYTES, ciphertext.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Ciphertext could not be decrypted", e);
        }
    }
}
