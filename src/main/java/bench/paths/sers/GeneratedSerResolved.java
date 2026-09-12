package bench.paths.sers;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Experiment: {@link GeneratedSer} with the per-call work hoisted into Jackson's {@code resolve} hook,
 * which is what Micronaut's generated serializers do in their constructor. Quarkus's generated
 * {@code serializeContent} re-decodes the inclusion, re-reads the property naming strategy and (through
 * {@code JacksonMapperUtil.serializePojo}) re-resolves the nested bean's serializer by class on EVERY
 * call; none of the three can change once the mapper is built.
 */
public abstract class GeneratedSerResolved extends StdSerializer<Object> {

    /** Resolved once: the inclusion for this bean class. */
    protected SerializationInclude include = SerializationInclude.ALWAYS;
    /** Resolved once: true when the inclusion cannot suppress anything (Micronaut's includeAll). */
    protected boolean includeAll = true;

    protected GeneratedSerResolved(Class<?> cls) {
        super(cls, false);
    }

    public abstract void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt);

    @Override
    public void resolve(SerializationContext ctxt) {
        include = decodeFor(handledType(), ctxt);
        includeAll = include == SerializationInclude.ALWAYS;
        resolveProperties(ctxt);
    }

    /** Resolve per-property state: final names, nested serializers. */
    protected abstract void resolveProperties(SerializationContext ctxt);

    /** {@code SerializationInclude.decode} without needing an instance of the bean. */
    private static SerializationInclude decodeFor(Class<?> beanClass, SerializationContext ctxt) {
        JsonInclude.Include include = ctxt.getDefaultPropertyInclusion(beanClass).getValueInclusion();
        return switch (include) {
            case NON_EMPTY -> SerializationInclude.NON_EMPTY;
            case NON_NULL -> SerializationInclude.NON_NULL;
            case NON_ABSENT -> SerializationInclude.NON_ABSENT;
            default -> SerializationInclude.ALWAYS;
        };
    }

    /**
     * The final name for a property: the naming strategy is fixed once the mapper is built, so apply it
     * here instead of branching on it at every write (Micronaut's precomputed {@code Keys}).
     */
    protected static SerializableString name(SerializationContext ctxt, String javaFieldName,
            SerializedString defaultName) {
        PropertyNamingStrategy strategy = ctxt.getConfig().getPropertyNamingStrategy();
        if (strategy == null) {
            return defaultName;
        }
        return new SerializedString(strategy.nameForField(null, null, javaFieldName));
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject();
        serializeContent(value, gen, ctxt);
        gen.writeEndObject();
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt,
            TypeSerializer typeSer) {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, ctxt,
                typeSer.typeId(value, JsonToken.START_OBJECT));
        serializeContent(value, gen, ctxt);
        typeSer.writeTypeSuffix(gen, ctxt, typeIdDef);
    }
}
