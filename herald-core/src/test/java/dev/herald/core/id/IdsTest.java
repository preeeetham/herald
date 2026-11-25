package dev.herald.core.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesVersion7WithRfcVariant() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTimestampSoIdsSortByCreationTime() {
        UUID earlier = Ids.newId(1_700_000_000_000L);
        UUID later = Ids.newId(1_700_000_000_001L);

        assertThat(Ids.epochMillis(earlier)).isEqualTo(1_700_000_000_000L);
        assertThat(earlier.compareTo(later)).isNegative();
    }

    @Test
    void rejectsNonV7Ids() {
        assertThatThrownBy(() -> Ids.epochMillis(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
}
