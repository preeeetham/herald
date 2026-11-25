package dev.herald.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EventTypesTest {

    @ParameterizedTest
    @ValueSource(strings = {"claim.created", "policy.endorsement.issued", "payment_due"})
    void acceptsWellFormedTypes(String type) {
        assertThat(EventTypes.isValidType(type)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Claim.Created", "claim..created", ".claim", "claim.", "claim.*", "1claim"})
    void rejectsMalformedTypes(String type) {
        assertThat(EventTypes.isValidType(type)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "*, claim.created, true",
        "claim.*, claim.created, true",
        "claim.*, claim.reserve.changed, true",
        "claim.*, claims.created, false",
        "claim.*, claim, false",
        "claim.created, claim.created, true",
        "claim.created, claim.updated, false"
    })
    void matchesSubscriptions(String pattern, String type, boolean expected) {
        assertThat(EventTypes.matches(pattern, type)).isEqualTo(expected);
    }

    @Test
    void validatesPatterns() {
        assertThat(EventTypes.isValidPattern("claim.*")).isTrue();
        assertThat(EventTypes.isValidPattern("*")).isTrue();
        assertThat(EventTypes.isValidPattern("claim*")).isFalse();
        assertThat(EventTypes.matchesAny(List.of("policy.*", "claim.created"), "claim.created")).isTrue();
    }
}
