package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Int5;

/**
 * Hand-written serializer in the shape a code generator emits - five ints: writeNumber, no writeString anywhere.
 * Property names are pre-encoded SerializedStrings, so the only run-time encoding is the value.
 *
 * Kept out of its caller so the writes show up in this method rather than being buried inside the
 * collection serializer, which makes perfasm readable. It does not change the result.
 */
public final class Int5Ser extends StdSerializer<Int5> {

    private static final SerializedString N0 = new SerializedString("p0");
    private static final SerializedString N1 = new SerializedString("p1");
    private static final SerializedString N2 = new SerializedString("p2");
    private static final SerializedString N3 = new SerializedString("p3");
    private static final SerializedString N4 = new SerializedString("p4");

    public Int5Ser() { super(Int5.class); }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Int5 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        g.writeName(N0);
        g.writeNumber(v.p0);
        g.writeName(N1);
        g.writeNumber(v.p1);
        g.writeName(N2);
        g.writeNumber(v.p2);
        g.writeName(N3);
        g.writeNumber(v.p3);
        g.writeName(N4);
        g.writeNumber(v.p4);
        g.writeEndObject();
    }
}
