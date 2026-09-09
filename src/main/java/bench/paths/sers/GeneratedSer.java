package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Copy of Quarkus's {@code GeneratedSerializer}, the base class every serializer it generates
 * extends. Runtime code, not generated. The braces live here and only the property writes are
 * generated, so a generated class declares exactly one method: {@code serializeContent}.
 *
 * <p>That split matters: {@code serializeContent}, not {@code serialize}, is the compile unit
 * holding the {@code writeString} call sites.
 */
public abstract class GeneratedSer extends StdSerializer<Object> {

    protected GeneratedSer(Class<?> cls) {
        super(cls, false);
    }

    public abstract void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt);

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
