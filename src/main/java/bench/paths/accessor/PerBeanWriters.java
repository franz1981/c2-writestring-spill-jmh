package bench.paths.accessor;

import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * Experiment: ONE generated writer class per bean (the accessor merged into the writer). The
 * writer switches on its property index and calls the getter directly, so the only virtual call
 * per property is Jackson's own {@code prop.serializeAsProperty}; the megamorphic
 * {@code accessor.stringGetter} dispatch of the accessor design is gone. Same class count as the
 * accessor design. Property order/index as in the generated accessors.
 */
public final class PerBeanWriters {

    private PerBeanWriters() {}

    abstract static class BeanWriter extends GeneratedPropertyWriters.GeneratedPropertyWriter {
        /**
         * plain() re-reads _suppressableValue/_typeSerializer/_serializer and compares a Class on every
         * property write; those three fields only change while Jackson resolves the serializer, so cache
         * the answer there instead (what Micronaut's generated serializers do with their includeAll flag).
         */
        private boolean plainCached;

        BeanWriter(BeanPropertyWriter base, int index) { super(base, null, index); recomputePlain(); }
        BeanWriter(BeanWriter base, PropertyName name) { super(base, name); recomputePlain(); }

        /** Default serializer class for the property at the given index, or null if it has no fast path. */
        abstract Class<?> defaultSerializerAt(int index);

        private void recomputePlain() {
            Class<?> expected = defaultSerializerAt(index);
            plainCached = expected != null && plain(expected);
        }

        final boolean plainFast() { return plainCached; }

        @Override public void assignSerializer(ValueSerializer<Object> ser) { super.assignSerializer(ser); recomputePlain(); }
        @Override public void assignNullSerializer(ValueSerializer<Object> ser) { super.assignNullSerializer(ser); recomputePlain(); }
        @Override public void assignTypeSerializer(TypeSerializer ts) { super.assignTypeSerializer(ts); recomputePlain(); }

        final void string(String value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (value == null || !plainFast()) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeString(value);
        }

        final void stringElement(String value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (value == null || !plainFast()) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeString(value);
        }

        final void integer(int value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (!plainFast()) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeNumber(value);
        }

        final void integerElement(int value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (!plainFast()) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeNumber(value);
        }
    }

    public static final class ExtendedPersonWriter extends BeanWriter {
        public ExtendedPersonWriter(BeanPropertyWriter base, int index) { super(base, index); }
        private ExtendedPersonWriter(ExtendedPersonWriter b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonWriter(this, n); }

        @Override Class<?> defaultSerializerAt(int i) {
            return switch (i) {
                case 1 -> NumberSerializers.IntegerSerializer.class;
                case 3, 4 -> StringSerializer.class;
                default -> null; // 0 address, 2 car: always the general path
            };
        }

        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            ExtendedPerson p = (ExtendedPerson) bean;
            switch (index) {
                case 0: writeValue(bean, p.getAddress(), gen, prov); return;
                case 1: integer(p.getAge(), bean, gen, prov); return;
                case 2: writeValue(bean, p.getCar(), gen, prov); return;
                case 3: string(p.getFirstName(), bean, gen, prov); return;
                case 4: string(p.getLastName(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }

        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            ExtendedPerson p = (ExtendedPerson) bean;
            switch (index) {
                case 0: writeElement(bean, p.getAddress(), gen, prov); return;
                case 1: integerElement(p.getAge(), bean, gen, prov); return;
                case 2: writeElement(bean, p.getCar(), gen, prov); return;
                case 3: stringElement(p.getFirstName(), bean, gen, prov); return;
                case 4: stringElement(p.getLastName(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }
    }

    public static final class AddressWriter extends BeanWriter {
        public AddressWriter(BeanPropertyWriter base, int index) { super(base, index); }
        private AddressWriter(AddressWriter b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new AddressWriter(this, n); }

        @Override Class<?> defaultSerializerAt(int i) { return StringSerializer.class; }

        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            Address a = (Address) bean;
            switch (index) {
                case 0: string(a.getCity(), bean, gen, prov); return;
                case 1: string(a.getStreet(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }

        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            Address a = (Address) bean;
            switch (index) {
                case 0: stringElement(a.getCity(), bean, gen, prov); return;
                case 1: stringElement(a.getStreet(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }
    }

    public static final class CarWriter extends BeanWriter {
        public CarWriter(BeanPropertyWriter base, int index) { super(base, index); }
        private CarWriter(CarWriter b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new CarWriter(this, n); }

        @Override Class<?> defaultSerializerAt(int i) { return StringSerializer.class; }

        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            Car c = (Car) bean;
            switch (index) {
                case 0: string(c.getBrand(), bean, gen, prov); return;
                case 1: string(c.getModel(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }

        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            Car c = (Car) bean;
            switch (index) {
                case 0: stringElement(c.getBrand(), bean, gen, prov); return;
                case 1: stringElement(c.getModel(), bean, gen, prov); return;
                default: throw new IllegalStateException();
            }
        }
    }

    /** Same discovery as GeneratedPropertyWriterModifier (index via the accessor), per-bean writer classes. */
    public static final class Modifier extends tools.jackson.databind.ser.ValueSerializerModifier {
        private final Map<Class<?>, GeneratedPropertyAccessor> accessors;

        public Modifier(Map<Class<?>, GeneratedPropertyAccessor> accessors) { this.accessors = accessors; }

        @Override
        public java.util.List<BeanPropertyWriter> changeProperties(tools.jackson.databind.SerializationConfig config,
                tools.jackson.databind.BeanDescription.Supplier beanDesc, java.util.List<BeanPropertyWriter> props) {
            Class<?> beanClass = beanDesc.getBeanClass();
            GeneratedPropertyAccessor accessor = accessors.get(beanClass);
            if (accessor == null) {
                return props;
            }
            for (int i = 0; i < props.size(); i++) {
                BeanPropertyWriter w = props.get(i);
                if (w.getClass() != BeanPropertyWriter.class || w.getMember() == null) {
                    continue;
                }
                int index = accessor.indexOf(w.getMember().getName());
                if (index < 0) {
                    continue;
                }
                if (beanClass == ExtendedPerson.class) props.set(i, new ExtendedPersonWriter(w, index));
                else if (beanClass == Address.class) props.set(i, new AddressWriter(w, index));
                else if (beanClass == Car.class) props.set(i, new CarWriter(w, index));
            }
            return props;
        }
    }
}
