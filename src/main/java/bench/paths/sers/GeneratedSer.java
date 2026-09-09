package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Stand-in for Quarkus's {@code GeneratedSerializer}, which every serializer it generates extends.
 * The braces live here and only the property writes are generated, so the generated class declares
 * exactly one method: {@code serializeContent}.
 *
 * <p>This is the same split the real one has:
 * <pre>
 *   serialize(v, g, ctxt) { g.writeStartObject(); serializeContent(v, g, ctxt); g.writeEndObject(); }
 * </pre>
 *
 * <p>It matters here because {@code serializeContent}, not {@code serialize}, is the compile unit
 * that holds the {@code writeString} calls - the 1:1 counterpart of {@code FlatSer.serialize} in
 * the Jackson 2 reproducer on {@code master}.
 */
public abstract class GeneratedSer<T> extends StdSerializer<T> {

    protected GeneratedSer(Class<T> t) { super(t); }

    public abstract void serializeContent(T value, JsonGenerator g, SerializationContext ctxt);

    @Override
    public void serialize(T value, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        serializeContent(value, g, ctxt);
        g.writeEndObject();
    }
}
