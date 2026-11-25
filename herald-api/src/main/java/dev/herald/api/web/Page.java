package dev.herald.api.web;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * A cursor-paginated slice. Because ids are UUIDv7 (time-ordered) the cursor is simply the last id
 * returned; the client passes it back as {@code ?after=}.
 */
public record Page<T>(List<T> data, String nextCursor) {

    public static <T> Page<T> of(List<T> rowsPlusOne, int limit, Function<T, UUID> idOf) {
        if (rowsPlusOne.size() <= limit) {
            return new Page<>(rowsPlusOne, null);
        }
        List<T> page = rowsPlusOne.subList(0, limit);
        return new Page<>(List.copyOf(page), idOf.apply(page.getLast()).toString());
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(data.stream().map(mapper).toList(), nextCursor);
    }
}
