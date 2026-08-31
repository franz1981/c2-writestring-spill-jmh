package bench.paths;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

/**
 * The <b>accessor</b> shape: each property is written by a small method that calls
 * {@code writeName} and {@code writeString} directly on the generator, with no
 * {@code ValueSerializer} indirection in between.
 * <p>
 * This mirrors Quarkus's {@code GeneratedPropertyWriters$StringWriter.serializeAsProperty} (55 bytes),
 * which C2 inlines into its caller — pulling {@code writeName} (206 bytes) and {@code writeString}
 * (118 bytes, containing the ASCII copy loop) into one oversized frame.
 */
public final class DirectSer extends StdSerializer<Bean5> {

    private static final SerializedString N0 = new SerializedString("p0");
    private static final SerializedString N1 = new SerializedString("p1");
    private static final SerializedString N2 = new SerializedString("p2");
    private static final SerializedString N3 = new SerializedString("p3");
    private static final SerializedString N4 = new SerializedString("p4");

    public DirectSer() {
        super(Bean5.class);
    }

    /** Small enough that C2 inlines it at every call site, exactly like the generated writer. */
    private static void prop(JsonGenerator g, SerializedString name, String value) {
        g.writeName(name);
        g.writeString(value);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Bean5 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        prop(g, N0, v.p0);
        prop(g, N1, v.p1);
        prop(g, N2, v.p2);
        prop(g, N3, v.p3);
        prop(g, N4, v.p4);
        g.writeEndObject();
    }
}
