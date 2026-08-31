package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Str5;

/**
 * Hand-written serializer in the shape a code generator emits - five Strings - the ExtendedPerson shape.
 * Property names are pre-encoded SerializedStrings, so the only run-time encoding is the value.
 *
 * Kept out of its caller so the writes show up in this method rather than being buried inside the
 * collection serializer, which makes perfasm readable. It does not change the result.
 */
public final class Str5Ser extends StdSerializer<Str5> {

    private static final SerializedString N0 = new SerializedString("p0");
    private static final SerializedString N1 = new SerializedString("p1");
    private static final SerializedString N2 = new SerializedString("p2");
    private static final SerializedString N3 = new SerializedString("p3");
    private static final SerializedString N4 = new SerializedString("p4");

    public Str5Ser() { super(Str5.class); }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Str5 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        g.writeName(N0);
        g.writeString(v.p0);
        g.writeName(N1);
        g.writeString(v.p1);
        g.writeName(N2);
        g.writeString(v.p2);
        g.writeName(N3);
        g.writeString(v.p3);
        g.writeName(N4);
        g.writeString(v.p4);
        g.writeEndObject();
    }
}
