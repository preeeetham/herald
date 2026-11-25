package dev.herald.worker.config;

import dev.herald.core.crypto.SecretCipher;
import dev.herald.core.net.EndpointUrlGuard;
import dev.herald.core.retry.RetryPolicy;
import dev.herald.core.signing.WebhookSigner;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
class WorkerBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretCipher secretCipher(WorkerProperties properties) {
        return SecretCipher.fromBase64(properties.secrets().encryptionKey());
    }

    @Bean
    WebhookSigner webhookSigner() {
        return new WebhookSigner();
    }

    @Bean
    EndpointUrlGuard endpointUrlGuard(WorkerProperties properties) {
        // Shape (scheme) was validated at registration; here we only re-check resolved addresses.
        return new EndpointUrlGuard(properties.delivery().allowPrivateNetworks(), false);
    }

    @Bean
    RetryPolicy retryPolicy(WorkerProperties properties) {
        var retry = properties.retry();
        return new RetryPolicy(retry.schedule(), retry.jitter(), retry.maxRetryAfter(), RandomGenerator.getDefault());
    }

    /** One client for all deliveries: virtual threads, no redirects (SSRF), bounded connect time. */
    @Bean(destroyMethod = "close")
    HttpClient webhookHttpClient(WorkerProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.delivery().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(WorkerProperties properties) {
        var builder = SqsClient.builder()
                .region(Region.of(properties.queue().region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClient(UrlConnectionHttpClient.create());
        if (properties.queue().endpoint() != null) {
            builder.endpointOverride(properties.queue().endpoint());
        }
        return builder.build();
    }
}
