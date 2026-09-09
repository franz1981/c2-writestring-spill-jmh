package bench.paths.sers;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.SerializationContext;

/**
 * Copy of Quarkus's {@code JacksonMapperUtil.SerializationInclude}, which is hand-written runtime
 * code rather than generated - the generated serializers call {@code decode} once per object and
 * {@code shouldSerialize} once per property.
 *
 * <p>Reproduced verbatim because its size is what keeps it out of line: C2's default
 * {@code MaxInlineSize} is 35 bytes and {@code shouldSerialize} is well over that, so it stays a
 * call, several per object. No inlining annotations here - that decision is C2's, as it is for the
 * real one.
 */
public enum SerializationInclude {

    ALWAYS,
    NON_NULL,
    NON_ABSENT,
    NON_EMPTY;

    public static SerializationInclude decode(Object object, SerializationContext serializationContext) {
        JsonInclude.Include include = serializationContext.getDefaultPropertyInclusion(object.getClass())
                .getValueInclusion();
        return switch (include) {
            case NON_EMPTY -> NON_EMPTY;
            case NON_NULL -> NON_NULL;
            case NON_ABSENT -> NON_ABSENT;
            default -> ALWAYS;
        };
    }

    public boolean shouldSerialize(Object value) {
        return switch (this) {
            case ALWAYS -> true;
            case NON_NULL -> value != null;
            case NON_ABSENT -> isPresent(value);
            case NON_EMPTY -> hasValue(value);
        };
    }

    private boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Optional o) {
            return o.isPresent();
        }
        return true;
    }

    private boolean hasValue(Object value) {
        if (!isPresent(value)) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        if (value instanceof Collection c) {
            return !c.isEmpty();
        }
        if (value instanceof Map m) {
            return !m.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }
}
