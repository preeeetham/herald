package dev.herald.core.retry;

import java.util.Set;

/** Decides whether an HTTP response from a receiver is worth retrying. */
public final class FailureClassifier {

    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 409, 425, 429);

    private FailureClassifier() {}

    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Server errors and a few client errors that signal a temporary condition are retryable.
     * Other 4xx responses are permanent rejections, and 3xx responses are treated as permanent
     * because redirects are never followed (the endpoint URL must be fixed instead).
     */
    public static boolean isRetryable(int statusCode) {
        return statusCode >= 500 || RETRYABLE_CLIENT_ERRORS.contains(statusCode);
    }
}
