package dev.herald.worker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed configuration for the worker, bound from {@code herald.*}. */
@Validated
@ConfigurationProperties("herald")
public record WorkerProperties(
        @Valid @NotNull Secrets secrets,
        @Valid @NotNull Queue queue,
        @Valid @NotNull Dispatch dispatch,
        @Valid @NotNull Delivery delivery,
        @Valid @NotNull Retry retry,
        @Valid @NotNull Isolation isolation,
        @Valid @NotNull Sweeper sweeper) {

    public record Secrets(@NotBlank String encryptionKey) {}

    /**
     * @param endpoint override for local development (LocalStack); empty in AWS
     * @param fairQueues set {@code MessageGroupId = tenantId} so SQS fair queues isolate tenants
     * @param createIfMissing create the queue on start-up (local only; Terraform owns it in AWS)
     */
    public record Queue(
            @NotBlank String name, @NotBlank String region, URI endpoint, boolean fairQueues, boolean createIfMissing,
            @Min(1) int consumers, @NotNull Duration visibilityTimeout, @NotNull Duration waitTime) {}

    /** @param perTenantLimit max rows one tenant can take per dispatch tick (fair dispatch) */
    public record Dispatch(@NotNull Duration interval, @Min(1) int batchSize, @Min(1) int perTenantLimit) {}

    public record Delivery(
            @Min(1) int maxInFlight,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout,
            @Min(0) int responseExcerptBytes,
            boolean allowPrivateNetworks,
            @NotBlank String userAgent) {}

    public record Retry(
            @NotEmpty List<Duration> schedule,
            @DecimalMin("0.0") @DecimalMax("0.99") double jitter,
            @NotNull Duration maxRetryAfter) {}

    /**
     * @param replicas number of worker replicas; a tenant's {@code max_concurrency} is divided across
     *     them (see ADR-0004)
     */
    public record Isolation(
            @Min(1) int replicas,
            @NotNull Duration bulkheadDeferral,
            @Min(1) int circuitWindowSize,
            @Min(1) int circuitMinimumCalls,
            @DecimalMin("1") @DecimalMax("100") float circuitFailureRatePercent,
            @NotNull Duration circuitOpenDuration) {}

    /** @param lease how long a row may stay QUEUED/DELIVERING before it is considered abandoned */
    public record Sweeper(@NotNull Duration interval, @NotNull Duration lease) {}
}
