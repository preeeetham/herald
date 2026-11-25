package dev.herald.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyTest {

    @Test
    void generatedKeyParsesBack() {
        ApiKey key = ApiKey.generate();

        assertThat(ApiKey.parse(key.value())).contains(key);
        assertThat(key.value()).matches("hk_[a-z0-9]{8}_[A-Za-z0-9]{32}");
    }

    @Test
    void matchesOnlyItsOwnHash() {
        ApiKey key = ApiKey.generate();

        assertThat(key.matches(key.hash())).isTrue();
        assertThat(ApiKey.generate().matches(key.hash())).isFalse();
    }

    @Test
    void rejectsMalformedKeys() {
        assertThat(ApiKey.parse(null)).isEmpty();
        assertThat(ApiKey.parse("hk_short_x")).isEmpty();
        assertThat(ApiKey.parse("sk_abcdefgh_" + "a".repeat(32))).isEmpty();
    }

    @Test
    void neverPrintsSecret() {
        ApiKey key = ApiKey.generate();

        assertThat(key.toString()).doesNotContain(key.secret());
    }
}
