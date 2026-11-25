package dev.herald.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher(randomKey());

    @Test
    void roundTrips() {
        byte[] sealed = cipher.encrypt("whsec_secret", "tenant-a");

        assertThat(cipher.decrypt(sealed, "tenant-a")).isEqualTo("whsec_secret");
    }

    @Test
    void usesFreshIvForEveryEncryption() {
        assertThat(cipher.encrypt("same", "t")).isNotEqualTo(cipher.encrypt("same", "t"));
    }

    @Test
    void ciphertextIsBoundToItsOwner() {
        byte[] sealed = cipher.encrypt("whsec_secret", "tenant-a");

        assertThatThrownBy(() -> cipher.decrypt(sealed, "tenant-b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsTampering() {
        byte[] sealed = cipher.encrypt("whsec_secret", "tenant-a");
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(sealed, "tenant-a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAes256Key() {
        assertThatThrownBy(() -> new SecretCipher(new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
