package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Str1;

/**
 * Hand-written serializer in the shape a code generator emits - one String property: a single writeName + writeString.
 * Property names are pre-encoded SerializedStrings, so the only run-time encoding is the value.
 *
 * Kept out of its caller so the writes show up in this method rather than being buried inside the
 * collection serializer, which makes perfasm readable. It does not change the result.
 */
public final class Str1Ser extends StdSerializer<Str1> {

    private static final SerializedString N0 = new SerializedString("p0");

    public Str1Ser() { super(Str1.class); }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Str1 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        g.writeName(N0);
        g.writeString(v.p0);
        g.writeEndObject();
    }
}
