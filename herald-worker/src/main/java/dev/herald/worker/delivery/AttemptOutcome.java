package dev.herald.worker.delivery;

/** Stored on {@code delivery_attempt.outcome}. */
public enum AttemptOutcome {
    SUCCEEDED,
    RETRYING,
    FAILED
}
