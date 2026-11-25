package dev.herald.worker.delivery;

import dev.herald.core.crypto.EndpointSecrets;
import dev.herald.core.crypto.SecretCipher;
import dev.herald.core.net.EndpointUrlGuard;
import dev.herald.core.net.UnsafeUrlException;
import dev.herald.core.retry.RetryAfter;
import dev.herald.core.signing.SigningSecret;
import dev.herald.core.signing.WebhookSigner;
import dev.herald.worker.config.WorkerProperties;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Performs one signed HTTP POST of an event to an endpoint. Never throws for receiver failures. */
@Component
public class WebhookSender {

    private final HttpClient http;
    private final WebhookSigner signer;
    private final SecretCipher cipher;
    private final EndpointUrlGuard urlGuard;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration requestTimeout;
    private final int excerptBytes;
    private final String userAgent;
    private final Tracer tracer;
    private final Propagator propagator;

    WebhookSender(
            HttpClient webhookHttpClient,
            WebhookSigner signer,
            SecretCipher cipher,
            EndpointUrlGuard urlGuard,
            JsonMapper json,
            Clock clock,
            Tracer tracer,
            Propagator propagator,
            WorkerProperties properties) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.http = webhookHttpClient;
        this.signer = signer;
        this.cipher = cipher;
        this.urlGuard = urlGuard;
        this.json = json;
        this.clock = clock;
        this.requestTimeout = properties.delivery().requestTimeout();
        this.excerptBytes = properties.delivery().responseExcerptBytes();
        this.userAgent = properties.delivery().userAgent();
    }

    public SendResult send(DeliveryJob job) {
        Instant started = clock.instant();
        URI uri;
        try {
            uri = URI.create(job.url());
            urlGuard.checkResolved(uri); // re-checked at send time: DNS may have changed since registration
        } catch (UnsafeUrlException | IllegalArgumentException e) {
            return SendResult.permanentError("Blocked: " + e.getMessage(), started, elapsed(started));
        }

        String body = body(job);
        long timestamp = started.getEpochSecond();
        String msgId = job.eventId().toString(); // stable across retries so receivers can deduplicate
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .header(WebhookSigner.HEADER_ID, msgId)
                .header(WebhookSigner.HEADER_TIMESTAMP, Long.toString(timestamp))
                .header(WebhookSigner.HEADER_SIGNATURE, signer.signatureHeader(secrets(job, started), msgId, timestamp, body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        TraceContext trace = tracer.currentTraceContext().context();
        if (trace != null) {
            propagator.inject(trace, request, HttpRequest.Builder::header);
        }

        try {
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            String excerpt = readExcerpt(response.body());
            return SendResult.response(
                    response.statusCode(),
                    excerpt,
                    started,
                    elapsed(started),
                    RetryAfter.parse(response.headers().firstValue("Retry-After").orElse(null), clock.instant()));
        } catch (HttpConnectTimeoutException e) {
            return SendResult.transientError("Connect timeout", started, elapsed(started));
        } catch (HttpTimeoutException e) {
            return SendResult.transientError("Timed out after " + requestTimeout.toMillis() + " ms", started, elapsed(started));
        } catch (IOException e) {
            return SendResult.transientError("Connection error: " + e.getClass().getSimpleName(), started, elapsed(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.transientError("Interrupted", started, elapsed(started));
        }
    }

    /** Standard Webhooks envelope: {@code {"id", "type", "timestamp", "data"}}. */
    String body(DeliveryJob job) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("id", job.eventId().toString());
        envelope.put("type", job.eventType());
        envelope.put("timestamp", job.eventCreatedAt().toString());
        envelope.set("data", json.readTree(job.payload()));
        return json.writeValueAsString(envelope);
    }

    private List<SigningSecret> secrets(DeliveryJob job, Instant now) {
        String aad = EndpointSecrets.associatedData(job.tenantId(), job.endpointId());
        List<SigningSecret> secrets = new ArrayList<>(2);
        secrets.add(SigningSecret.parse(cipher.decrypt(job.secretCiphertext(), aad)));
        if (job.previousSecretCiphertext() != null && job.previousSecretExpiresAt() != null && now.isBefore(job.previousSecretExpiresAt())) {
            secrets.add(SigningSecret.parse(cipher.decrypt(job.previousSecretCiphertext(), aad)));
        }
        return secrets;
    }

    private String readExcerpt(InputStream body) throws IOException {
        try (body) {
            byte[] bytes = body.readNBytes(excerptBytes);
            return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Duration elapsed(Instant started) {
        return Duration.between(started, clock.instant());
    }
}
