package dev.herald.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointUrlGuardTest {

    private final EndpointUrlGuard production = new EndpointUrlGuard(false, true);
    private final EndpointUrlGuard local = new EndpointUrlGuard(true, false);

    @Test
    void acceptsPublicHttpsUrl() {
        assertThat(production.validate("https://partner.example.com/hooks/claims").getHost())
                .isEqualTo("partner.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://169.254.169.254/latest/meta-data",
        "https://127.0.0.1/",
        "https://10.0.0.5/",
        "https://172.16.0.1/",
        "https://192.168.1.1/",
        "https://100.64.0.1/",
        "https://0.0.0.0/",
        "https://[::1]/",
        "https://[fd00:ec2::254]/",
        "https://[::ffff:127.0.0.1]/",
        "https://localhost/",
        "https://db.internal/"
    })
    void rejectsNonPublicTargets(String url) {
        assertThatThrownBy(() -> production.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://partner.example.com", "ftp://partner.example.com", "https://user:pw@example.com", "not a url", ""})
    void rejectsBadShape(String url) {
        assertThatThrownBy(() -> production.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void localModeAllowsPlainHttpToLocalhost() {
        assertThat(local.validate("http://localhost:8089/hooks").getPort()).isEqualTo(8089);
    }
}
