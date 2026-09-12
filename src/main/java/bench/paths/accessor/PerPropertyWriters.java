package bench.paths.accessor;

import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * Experiment: one generated writer class per bean property, calling the getter directly - no
 * per-bean accessor, no index, no megamorphic {@code stringGetter} call. What Quarkus would
 * generate at build time instead of the accessor; hand-written here for the three beans.
 * The value/fast-path logic is the typed writers' verbatim.
 */
public final class PerPropertyWriters {

    private PerPropertyWriters() {}

    /** The writer for (bean class, member name), or null to keep Jackson's. */
    public static BeanPropertyWriter create(BeanPropertyWriter base, Class<?> beanClass, String member) {
        if (beanClass == ExtendedPerson.class) {
            switch (member) {
                case "getFirstName": return new ExtendedPersonFirstName(base);
                case "getLastName": return new ExtendedPersonLastName(base);
                case "getAge": return new ExtendedPersonAge(base);
                case "getAddress": return new ExtendedPersonAddress(base);
                case "getCar": return new ExtendedPersonCar(base);
                default: return null;
            }
        }
        if (beanClass == Address.class) {
            switch (member) {
                case "getCity": return new AddressCity(base);
                case "getStreet": return new AddressStreet(base);
                default: return null;
            }
        }
        if (beanClass == Car.class) {
            switch (member) {
                case "getBrand": return new CarBrand(base);
                case "getModel": return new CarModel(base);
                default: return null;
            }
        }
        return null;
    }

    /** Base with the same slow paths as the accessor design's writers (accessor unused). */
    abstract static class Direct extends GeneratedPropertyWriters.GeneratedPropertyWriter {
        Direct(BeanPropertyWriter base) { super(base, null, -1); }
        Direct(Direct base, PropertyName name) { super(base, name); }

        final void string(String value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (value == null || !plain(StringSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeString(value);
        }

        final void stringElement(String value, Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            if (value == null || !plain(StringSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeString(value);
        }
    }

    static final class ExtendedPersonFirstName extends Direct {
        ExtendedPersonFirstName(BeanPropertyWriter base) { super(base); }
        private ExtendedPersonFirstName(ExtendedPersonFirstName b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonFirstName(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((ExtendedPerson) bean).getFirstName(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((ExtendedPerson) bean).getFirstName(), bean, gen, prov);
        }
    }

    static final class ExtendedPersonLastName extends Direct {
        ExtendedPersonLastName(BeanPropertyWriter base) { super(base); }
        private ExtendedPersonLastName(ExtendedPersonLastName b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonLastName(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((ExtendedPerson) bean).getLastName(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((ExtendedPerson) bean).getLastName(), bean, gen, prov);
        }
    }

    static final class AddressCity extends Direct {
        AddressCity(BeanPropertyWriter base) { super(base); }
        private AddressCity(AddressCity b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new AddressCity(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((Address) bean).getCity(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((Address) bean).getCity(), bean, gen, prov);
        }
    }

    static final class AddressStreet extends Direct {
        AddressStreet(BeanPropertyWriter base) { super(base); }
        private AddressStreet(AddressStreet b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new AddressStreet(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((Address) bean).getStreet(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((Address) bean).getStreet(), bean, gen, prov);
        }
    }

    static final class CarBrand extends Direct {
        CarBrand(BeanPropertyWriter base) { super(base); }
        private CarBrand(CarBrand b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new CarBrand(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((Car) bean).getBrand(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((Car) bean).getBrand(), bean, gen, prov);
        }
    }

    static final class CarModel extends Direct {
        CarModel(BeanPropertyWriter base) { super(base); }
        private CarModel(CarModel b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new CarModel(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            string(((Car) bean).getModel(), bean, gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            stringElement(((Car) bean).getModel(), bean, gen, prov);
        }
    }

    static final class ExtendedPersonAge extends Direct {
        ExtendedPersonAge(BeanPropertyWriter base) { super(base); }
        private ExtendedPersonAge(ExtendedPersonAge b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonAge(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            int value = ((ExtendedPerson) bean).getAge();
            if (!plain(NumberSerializers.IntegerSerializer.class)) {
                writeValue(bean, value, gen, prov);
                return;
            }
            gen.writeName(_name);
            gen.writeNumber(value);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            int value = ((ExtendedPerson) bean).getAge();
            if (!plain(NumberSerializers.IntegerSerializer.class)) {
                writeElement(bean, value, gen, prov);
                return;
            }
            gen.writeNumber(value);
        }
    }

    static final class ExtendedPersonAddress extends Direct {
        ExtendedPersonAddress(BeanPropertyWriter base) { super(base); }
        private ExtendedPersonAddress(ExtendedPersonAddress b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonAddress(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeValue(bean, ((ExtendedPerson) bean).getAddress(), gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeElement(bean, ((ExtendedPerson) bean).getAddress(), gen, prov);
        }
    }

    static final class ExtendedPersonCar extends Direct {
        ExtendedPersonCar(BeanPropertyWriter base) { super(base); }
        private ExtendedPersonCar(ExtendedPersonCar b, PropertyName n) { super(b, n); }
        @Override protected BeanPropertyWriter _new(PropertyName n) { return new ExtendedPersonCar(this, n); }
        @Override public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeValue(bean, ((ExtendedPerson) bean).getCar(), gen, prov);
        }
        @Override public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            writeElement(bean, ((ExtendedPerson) bean).getCar(), gen, prov);
        }
    }

    /** The modifier variant: same discovery as the accessor modifier, per-property writer classes instead. */
    public static final class Modifier extends tools.jackson.databind.ser.ValueSerializerModifier {
        private final Map<Class<?>, GeneratedPropertyAccessor> accessors;

        public Modifier(Map<Class<?>, GeneratedPropertyAccessor> accessors) {
            this.accessors = accessors;
        }

        @Override
        public java.util.List<BeanPropertyWriter> changeProperties(tools.jackson.databind.SerializationConfig config,
                tools.jackson.databind.BeanDescription.Supplier beanDesc, java.util.List<BeanPropertyWriter> beanProperties) {
            Class<?> beanClass = beanDesc.getBeanClass();
            if (!accessors.containsKey(beanClass)) {
                return beanProperties;
            }
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanPropertyWriter writer = beanProperties.get(i);
                if (writer.getClass() != BeanPropertyWriter.class || writer.getMember() == null) {
                    continue;
                }
                BeanPropertyWriter generated = create(writer, beanClass, writer.getMember().getName());
                if (generated != null) {
                    beanProperties.set(i, generated);
                }
            }
            return beanProperties;
        }
    }
}
