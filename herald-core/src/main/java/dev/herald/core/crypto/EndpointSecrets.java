package dev.herald.core.crypto;

import java.util.UUID;

/**
 * Shared contract between the API (which encrypts endpoint secrets) and the worker (which decrypts
 * them to sign deliveries). Keeping it in one place prevents the two from drifting apart.
 */
public final class EndpointSecrets {

    private EndpointSecrets() {}

    /** Associated data binding an encrypted secret to its tenant and endpoint. */
    public static String associatedData(UUID tenantId, UUID endpointId) {
        return tenantId + ":" + endpointId;
    }
}
