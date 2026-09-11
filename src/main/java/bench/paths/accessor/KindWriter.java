package bench.paths.accessor;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.jdk.BooleanSerializer;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;

/**
 * Experiment: ONE writer class for every property kind, dispatching on an {@code int kind} with a
 * switch, instead of the six {@code GeneratedPropertyWriters} subclasses. The point is the call site
 * {@code prop.serializeAsProperty(bean, gen, prov)} in {@code UnrolledBeanSerializer}: with one
 * writer class it is monomorphic and C2 may inline the writer - and its copy loop - into the bean
 * serializer, which is the register-pressure shape of the generated serializers. Same value-reading
 * and fast-path logic as the typed writers, verbatim.
 */
public final class KindWriter extends GeneratedPropertyWriters.GeneratedPropertyWriter {
    private final int kind;

    KindWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index, int kind) {
        super(base, accessor, index);
        this.kind = kind;
    }

    private KindWriter(KindWriter base, PropertyName name) {
        super(base, name);
        this.kind = base.kind;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new KindWriter(this, newName);
    }

    // Tiny dispatcher (under FreqInlineSize) so the monomorphic call site in UnrolledBeanSerializer can
    // inline it; the per-kind bodies are small static helpers that C2 inlines in turn.
    @Override
    public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        switch (kind) {
            case GeneratedPropertyAccessor.KIND_STRING: string(this, bean, gen, prov); return;
            case GeneratedPropertyAccessor.KIND_INT: integer(this, bean, gen, prov); return;
            case GeneratedPropertyAccessor.KIND_LONG: longValue(this, bean, gen, prov); return;
            case GeneratedPropertyAccessor.KIND_BOOLEAN: bool(this, bean, gen, prov); return;
            case GeneratedPropertyAccessor.KIND_DOUBLE: dbl(this, bean, gen, prov); return;
            default: object(this, bean, gen, prov);
        }
    }

    private static void string(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        String value = w.accessor.stringGetter(bean, w.index);
        if (value == null || !w.plain(StringSerializer.class)) {
            w.writeValue(bean, value, gen, prov);
            return;
        }
        gen.writeName(w._name);
        gen.writeString(value);
    }

    private static void integer(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        int value = w.accessor.intGetter(bean, w.index);
        if (!w.plain(NumberSerializers.IntegerSerializer.class)) {
            w.writeValue(bean, value, gen, prov);
            return;
        }
        gen.writeName(w._name);
        gen.writeNumber(value);
    }

    private static void longValue(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        long value = w.accessor.longGetter(bean, w.index);
        if (!w.plain(NumberSerializers.LongSerializer.class)) {
            w.writeValue(bean, value, gen, prov);
            return;
        }
        gen.writeName(w._name);
        gen.writeNumber(value);
    }

    private static void bool(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        boolean value = w.accessor.booleanGetter(bean, w.index);
        if (!w.plain(BooleanSerializer.class)) {
            w.writeValue(bean, value, gen, prov);
            return;
        }
        gen.writeName(w._name);
        gen.writeBoolean(value);
    }

    private static void dbl(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        double value = w.accessor.doubleGetter(bean, w.index);
        if (!w.plain(NumberSerializers.DoubleSerializer.class)) {
            w.writeValue(bean, value, gen, prov);
            return;
        }
        gen.writeName(w._name);
        gen.writeNumber(value);
    }

    private static void object(KindWriter w, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        Object value = w.accessor.objectGetter(bean, w.index);
        w.writeValue(bean, value, gen, prov);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        switch (kind) {
            case GeneratedPropertyAccessor.KIND_STRING: {
                String value = accessor.stringGetter(bean, index);
                if (value == null || !plain(StringSerializer.class)) {
                    writeElement(bean, value, gen, prov);
                    return;
                }
                gen.writeString(value);
                return;
            }
            case GeneratedPropertyAccessor.KIND_INT: {
                int value = accessor.intGetter(bean, index);
                if (!plain(NumberSerializers.IntegerSerializer.class)) {
                    writeElement(bean, value, gen, prov);
                    return;
                }
                gen.writeNumber(value);
                return;
            }
            case GeneratedPropertyAccessor.KIND_LONG: {
                long value = accessor.longGetter(bean, index);
                if (!plain(NumberSerializers.LongSerializer.class)) {
                    writeElement(bean, value, gen, prov);
                    return;
                }
                gen.writeNumber(value);
                return;
            }
            case GeneratedPropertyAccessor.KIND_BOOLEAN: {
                boolean value = accessor.booleanGetter(bean, index);
                if (!plain(BooleanSerializer.class)) {
                    writeElement(bean, value, gen, prov);
                    return;
                }
                gen.writeBoolean(value);
                return;
            }
            case GeneratedPropertyAccessor.KIND_DOUBLE: {
                double value = accessor.doubleGetter(bean, index);
                if (!plain(NumberSerializers.DoubleSerializer.class)) {
                    writeElement(bean, value, gen, prov);
                    return;
                }
                gen.writeNumber(value);
                return;
            }
            default: {
                Object value = accessor.objectGetter(bean, index);
                writeElement(bean, value, gen, prov);
            }
        }
    }
}
