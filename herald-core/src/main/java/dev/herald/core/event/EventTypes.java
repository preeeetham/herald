package dev.herald.core.event;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Event type naming and subscription matching.
 *
 * <p>Types are dot-separated lowercase segments, e.g. {@code claim.created} or {@code
 * policy.endorsement.issued}. Subscriptions may be an exact type, a prefix wildcard such as
 * {@code claim.*} (matching any type below {@code claim}), or {@code *} for everything.
 */
public final class EventTypes {

    public static final String WILDCARD = "*";
    private static final Pattern TYPE = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){0,7}$");
    private static final int MAX_LENGTH = 128;

    private EventTypes() {}

    public static boolean isValidType(String type) {
        return type != null && type.length() <= MAX_LENGTH && TYPE.matcher(type).matches();
    }

    public static boolean isValidPattern(String pattern) {
        if (WILDCARD.equals(pattern)) {
            return true;
        }
        if (pattern != null && pattern.endsWith(".*")) {
            return isValidType(pattern.substring(0, pattern.length() - 2));
        }
        return isValidType(pattern);
    }

    public static boolean matches(String pattern, String type) {
        if (WILDCARD.equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return type.startsWith(prefix);
        }
        return pattern.equals(type);
    }

    public static boolean matchesAny(Collection<String> patterns, String type) {
        return patterns.stream().anyMatch(pattern -> matches(pattern, type));
    }
}
