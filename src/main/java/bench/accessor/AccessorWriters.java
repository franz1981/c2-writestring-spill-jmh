package bench.accessor;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.ser.jdk.BooleanSerializer;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;

/**
 * A transcription of Quarkus's {@code GeneratedPropertyWriters}: {@link BeanPropertyWriter}s that read
 * the property through a {@link PropertyAccessor} instead of a MethodHandle, and write scalars straight
 * to the generator when the property uses Jackson's default serializer, no inclusion filtering and no
 * type serializer. Every other case falls back to {@link GeneratedPropertyWriter#writeValue}, which is
 * {@code BeanPropertyWriter.serializeAsProperty} from the point where the value has been read.
 *
 * <p>The fallback is kept even though the benchmark never takes it: it is part of the method's bytecode
 * and therefore part of what the inliner and the register allocator see.
 */
public final class AccessorWriters {

    private AccessorWriters() {
    }

    public abstract static class GeneratedPropertyWriter extends BeanPropertyWriter {
        protected final PropertyAccessor accessor;
        protected final int index;

        GeneratedPropertyWriter(BeanPropertyWriter base, PropertyAccessor accessor, int index) {
            super(base);
            this.accessor = accessor;
            this.index = index;
        }

        GeneratedPropertyWriter(GeneratedPropertyWriter base, PropertyName name) {
            super(base, name);
            this.accessor = base.accessor;
            this.index = base.index;
        }

        protected final boolean plain(Class<?> defaultSerializer) {
            return _suppressableValue == null && _typeSerializer == null
                    && (_serializer == null || _serializer.getClass() == defaultSerializer);
        }

        /** BeanPropertyWriter#serializeAsProperty from the point where the value has been read. */
        protected final void writeValue(Object bean, Object value, JsonGenerator gen, SerializationContext prov)
                throws Exception {
            if (value == null) {
                if ((_suppressableValue != null) && prov.includeFilterSuppressNulls(_suppressableValue)) {
                    return;
                }
                if (_nullSerializer != null) {
                    gen.writeName(_name);
                    _nullSerializer.serialize(null, gen, prov);
                }
                return;
            }
            ValueSerializer<Object> ser = _serializer;
            if (ser == null) {
                Class<?> cls = value.getClass();
                PropertySerializerMap m = _dynamicSerializers;
                ser = m.serializerFor(cls);
                if (ser == null) {
                    ser = _findAndAddDynamic(m, cls, prov);
                }
            }
            if (_suppressableValue != null) {
                if (MARKER_FOR_EMPTY == _suppressableValue) {
                    if (ser.isEmpty(prov, value)) {
                        return;
                    }
                } else if (_suppressableValue.equals(value)) {
                    return;
                }
            }
            if (value == bean) {
                if (_handleSelfReference(bean, gen, prov, ser)) {
                    return;
                }
            }
            gen.writeName(_name);
            if (_typeSerializer == null) {
                ser.serialize(value, gen, prov);
            } else {
                ser.serializeWithType(value, gen, prov, _typeSerializer);
            }
        }
    }

    public static final class StringWriter extends GeneratedPropertyWriter {
        StringWriter(BeanPropertyWriter base, PropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private StringWriter(StringWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new StringWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            String value = accessor.stringGetter(bean, index);
            if (value == null || !plain(StringSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeString(value);
        }
    }

    public static final class IntWriter extends GeneratedPropertyWriter {
        IntWriter(BeanPropertyWriter base, PropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private IntWriter(IntWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new IntWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            int value = accessor.intGetter(bean, index);
            if (!plain(NumberSerializers.IntegerSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeNumber(value);
        }
    }

    public static final class BooleanWriter extends GeneratedPropertyWriter {
        BooleanWriter(BeanPropertyWriter base, PropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private BooleanWriter(BooleanWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new BooleanWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            boolean value = accessor.booleanGetter(bean, index);
            if (!plain(BooleanSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeBoolean(value);
        }
    }

    /** Kind tags, as the generated register uses. */
    public static final int KIND_STRING = 0;
    public static final int KIND_INT = 1;
    public static final int KIND_BOOLEAN = 2;

    public static BeanPropertyWriter create(BeanPropertyWriter base, PropertyAccessor accessor, int index, int kind) {
        switch (kind) {
            case KIND_STRING:
                return new StringWriter(base, accessor, index);
            case KIND_INT:
                return new IntWriter(base, accessor, index);
            case KIND_BOOLEAN:
                return new BooleanWriter(base, accessor, index);
            default:
                return base;
        }
    }
}
