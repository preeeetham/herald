package dev.herald.core.retry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FailureClassifierTest {

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 202, 204, 299})
    void twoHundredsAreSuccess(int status) {
        assertThat(FailureClassifier.isSuccess(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 425, 429, 500, 502, 503, 504})
    void temporaryFailuresAreRetried(int status) {
        assertThat(FailureClassifier.isRetryable(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 400, 401, 403, 404, 410, 413, 422})
    void permanentFailuresAreNotRetried(int status) {
        assertThat(FailureClassifier.isRetryable(status)).isFalse();
    }
}
