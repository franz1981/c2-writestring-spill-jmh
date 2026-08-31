package bench.paths;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

/**
 * The <b>reflection</b> shape: every property goes through a polymorphic interface call, so the
 * per-property writer is not inlined into the serializer and each write ends up compiled in its own
 * small frame.
 * <p>
 * This mirrors Jackson's own path, where {@code BeanPropertyWriter.serializeAsProperty} (210 bytes) is
 * too large to inline into the unrolled serializer and, when compiled on its own, refuses
 * {@code writeName} with "callee is too large". The point of this benchmark is that the *work* is
 * identical to {@link DirectSer} — only the inlining shape differs.
 */
public final class IndirectSer extends StdSerializer<Bean5> {

    /** Several implementations so the call site is megamorphic and C2 will not devirtualise it. */
    interface Prop {
        void write(Bean5 v, JsonGenerator g);
    }

    static final class P0 implements Prop {
        private final SerializedString n = new SerializedString("p0");
        public void write(Bean5 v, JsonGenerator g) { g.writeName(n); g.writeString(v.p0); }
    }
    static final class P1 implements Prop {
        private final SerializedString n = new SerializedString("p1");
        public void write(Bean5 v, JsonGenerator g) { g.writeName(n); g.writeString(v.p1); }
    }
    static final class P2 implements Prop {
        private final SerializedString n = new SerializedString("p2");
        public void write(Bean5 v, JsonGenerator g) { g.writeName(n); g.writeString(v.p2); }
    }
    static final class P3 implements Prop {
        private final SerializedString n = new SerializedString("p3");
        public void write(Bean5 v, JsonGenerator g) { g.writeName(n); g.writeString(v.p3); }
    }
    static final class P4 implements Prop {
        private final SerializedString n = new SerializedString("p4");
        public void write(Bean5 v, JsonGenerator g) { g.writeName(n); g.writeString(v.p4); }
    }

    private final Prop[] props = { new P0(), new P1(), new P2(), new P3(), new P4() };

    public IndirectSer() {
        super(Bean5.class);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serialize(Bean5 v, JsonGenerator g, SerializationContext ctxt) {
        g.writeStartObject();
        // one shared, megamorphic call site - the boundary reflection gets for free
        for (int i = 0; i < props.length; i++) {
            props[i].write(v, g);
        }
        g.writeEndObject();
    }
}
