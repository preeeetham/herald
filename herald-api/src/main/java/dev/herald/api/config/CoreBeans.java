package dev.herald.api.config;

import dev.herald.core.crypto.SecretCipher;
import dev.herald.core.net.EndpointUrlGuard;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes framework-free {@code herald-core} components as Spring beans. */
@Configuration(proxyBeanMethods = false)
class CoreBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretCipher secretCipher(HeraldProperties properties) {
        return SecretCipher.fromBase64(properties.secrets().encryptionKey());
    }

    @Bean
    EndpointUrlGuard endpointUrlGuard(HeraldProperties properties) {
        var endpoints = properties.endpoints();
        return new EndpointUrlGuard(endpoints.allowPrivateNetworks(), endpoints.requireHttps());
    }
}
