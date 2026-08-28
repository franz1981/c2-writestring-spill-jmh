package bench.flat;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

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
     * CollectionSerializer::serializeContents, which makes the perfasm output readable. It does not change
     * the result - the spill and the gap are there with or without it.
     */
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Flat v, JsonGenerator g, SerializerProvider p) throws IOException {
        g.writeStartObject();
        g.writeFieldName(S0);
        g.writeString(v.s0);
        g.writeEndObject();
    }
}
