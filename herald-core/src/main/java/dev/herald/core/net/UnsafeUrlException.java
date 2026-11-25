package dev.herald.core.net;

/** Raised when an endpoint URL could be used to reach infrastructure it must not reach. */
public final class UnsafeUrlException extends RuntimeException {

    public UnsafeUrlException(String message) {
        super(message);
    }
}
