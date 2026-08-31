package bench.flat;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

/**
 * Hand-written serializer in the shape a code generator emits: the property name is a pre-encoded
 * {@link SerializedString}, so the only string encoding left at run time is the value.
 */
public final class FlatSer extends StdSerializer<Flat> {

    private static final SerializedString S0 = new SerializedString("s0");

    public FlatSer() {
        super(Flat.class);
    }

    /**
     * Kept out of its caller so that the copy loop shows up in this method instead of being buried inside
     * the collection serializer, which makes the perfasm output readable. It does not change the result.
     */
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Flat v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        g.writeName(S0);
        g.writeString(v.s0);
        g.writeEndObject();
    }
}
