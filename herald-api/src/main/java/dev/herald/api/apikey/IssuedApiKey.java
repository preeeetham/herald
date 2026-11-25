package dev.herald.api.apikey;

import java.time.Instant;
import java.util.UUID;

/** Returned once, at creation: the only time the full key value leaves the service. */
public record IssuedApiKey(UUID id, String name, String prefix, String key, Instant createdAt) {}
