package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Mixed4;

/**
 * Hand-written serializer in the shape a code generator emits, for the Quarkus {@code Person}
 * shape: String, String, int, double - written INTERLEAVED: the two number writes sit
 * between the two String writes, so the number formatting is laid out between the copy loops.
 *
 * Kept out of its caller for the same reason as the others: it makes perfasm readable and does
 * not change the result.
 */
public final class Mixed4xSer extends StdSerializer<Mixed4> {

    private static final SerializedString N0 = new SerializedString("firstName");
    private static final SerializedString N1 = new SerializedString("lastName");
    private static final SerializedString N2 = new SerializedString("age");
    private static final SerializedString N3 = new SerializedString("height");

    public Mixed4xSer() { super(Mixed4.class); }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Mixed4 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        g.writeName(N0);
        g.writeString(v.firstName);
        g.writeName(N2);
        g.writeNumber(v.age);
        g.writeName(N3);
        g.writeNumber(v.height);
        g.writeName(N1);
        g.writeString(v.lastName);
        g.writeEndObject();
    }
}
