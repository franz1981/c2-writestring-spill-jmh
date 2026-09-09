package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

/**
 * Stand-in for Quarkus's {@code JacksonMapperUtil}. The generated serializers call these two
 * statics, so the compiled shape depends on them being here rather than inlined by hand:
 * {@code serializePojo} is where the nested beans' serializers get inlined, which is why in the
 * application it holds four copy loops (two Strings each from {@code Address} and {@code Car}).
 */
public final class MapperUtil {

    private MapperUtil() {}

    public static void writeFieldName(JsonGenerator gen, PropertyNamingStrategy strategy,
            String javaFieldName, SerializableString defaultName) {
        if (strategy == null) {
            gen.writeName(defaultName);
        } else {
            gen.writeName(strategy.nameForField(null, null, javaFieldName));
        }
    }

    /** Not inlined: the second of the two physical frames in the application's profile. */
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static void serializePojo(Object value, Object bean, JsonGenerator generator,
            SerializationContext ctxt) {
        if (value == null) {
            generator.writePOJO(value);
            return;
        }
        ValueSerializer<Object> serializer = ctxt.findTypedValueSerializer(value.getClass(), true);
        if (serializer != null) {
            serializer.serialize(value, generator, ctxt);
        } else {
            generator.writePOJO(value);
        }
    }
}
