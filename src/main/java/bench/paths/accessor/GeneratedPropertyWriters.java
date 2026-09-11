package bench.paths.accessor;

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
 * {@link BeanPropertyWriter}s that read the property through a {@link GeneratedPropertyAccessor} instead of
 * reflection and write scalars straight to the generator when the property uses Jackson's default serializer, no
 * inclusion filtering and no type serializer; every other case follows {@code BeanPropertyWriter#serializeAsProperty}
 * exactly, with the value coming from the accessor.
 */
public final class GeneratedPropertyWriters {

    private GeneratedPropertyWriters() {
    }

    public static BeanPropertyWriter create(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index,
            int kind) {
        return switch (kind) {
            case GeneratedPropertyAccessor.KIND_STRING -> new StringWriter(base, accessor, index);
            case GeneratedPropertyAccessor.KIND_INT -> new IntWriter(base, accessor, index);
            case GeneratedPropertyAccessor.KIND_LONG -> new LongWriter(base, accessor, index);
            case GeneratedPropertyAccessor.KIND_BOOLEAN -> new BooleanWriter(base, accessor, index);
            case GeneratedPropertyAccessor.KIND_DOUBLE -> new DoubleWriter(base, accessor, index);
            case GeneratedPropertyAccessor.KIND_OBJECT -> new ObjectWriter(base, accessor, index);
            default -> base;
        };
    }

    abstract static class GeneratedPropertyWriter extends BeanPropertyWriter {
        protected final GeneratedPropertyAccessor accessor;
        protected final int index;

        GeneratedPropertyWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
            super(base);
            this.accessor = accessor;
            this.index = index;
        }

        GeneratedPropertyWriter(GeneratedPropertyWriter base, PropertyName name) {
            super(base, name);
            this.accessor = base.accessor;
            this.index = base.index;
        }

        /** {@code BeanPropertyWriter#serializeAsProperty} from the point where the value has been read. */
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

        /** {@code BeanPropertyWriter#serializeAsElement} from the point where the value has been read. */
        protected final void writeElement(Object bean, Object value, JsonGenerator gen, SerializationContext prov)
                throws Exception {
            if (value == null) {
                if (_nullSerializer != null) {
                    _nullSerializer.serialize(null, gen, prov);
                } else {
                    // an element cannot be suppressed: the array position has to be filled
                    gen.writeNull();
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
                        serializeAsOmittedElement(bean, gen, prov);
                        return;
                    }
                } else if (_suppressableValue.equals(value)) {
                    serializeAsOmittedElement(bean, gen, prov);
                    return;
                }
            }
            if (value == bean) {
                if (_handleSelfReference(bean, gen, prov, ser)) {
                    return;
                }
            }
            if (_typeSerializer == null) {
                ser.serialize(value, gen, prov);
            } else {
                ser.serializeWithType(value, gen, prov, _typeSerializer);
            }
        }

        protected final boolean plain(Class<?> defaultSerializer) {
            return _suppressableValue == null && _typeSerializer == null
                    && (_serializer == null || _serializer.getClass() == defaultSerializer);
        }
    }

    static final class StringWriter extends GeneratedPropertyWriter {
        StringWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
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

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            String value = accessor.stringGetter(bean, index);
            if (value == null || !plain(StringSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeString(value);
        }

    }

    static final class IntWriter extends GeneratedPropertyWriter {
        IntWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
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

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            int value = accessor.intGetter(bean, index);
            if (!plain(NumberSerializers.IntegerSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeNumber(value);
        }

    }

    static final class LongWriter extends GeneratedPropertyWriter {
        LongWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private LongWriter(LongWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new LongWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            long value = accessor.longGetter(bean, index);
            if (!plain(NumberSerializers.LongSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeNumber(value);
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            long value = accessor.longGetter(bean, index);
            if (!plain(NumberSerializers.LongSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeNumber(value);
        }

    }

    static final class BooleanWriter extends GeneratedPropertyWriter {
        BooleanWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
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

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            boolean value = accessor.booleanGetter(bean, index);
            if (!plain(BooleanSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeBoolean(value);
        }

    }

    static final class DoubleWriter extends GeneratedPropertyWriter {
        DoubleWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private DoubleWriter(DoubleWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new DoubleWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            double value = accessor.doubleGetter(bean, index);
            if (!plain(NumberSerializers.DoubleSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeNumber(value);
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            double value = accessor.doubleGetter(bean, index);
            if (!plain(NumberSerializers.DoubleSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeNumber(value);
        }

    }

    static final class ObjectWriter extends GeneratedPropertyWriter {
        ObjectWriter(BeanPropertyWriter base, GeneratedPropertyAccessor accessor, int index) {
            super(base, accessor, index);
        }

        private ObjectWriter(ObjectWriter base, PropertyName name) {
            super(base, name);
        }

        @Override
        protected BeanPropertyWriter _new(PropertyName newName) {
            return new ObjectWriter(this, newName);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeValue(bean, accessor.objectGetter(bean, index), gen, prov);
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeElement(bean, accessor.objectGetter(bean, index), gen, prov);
        }

    }
}
