package bench.paths.accessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * Experiment: a generated serializer PER BEAN that keeps Jackson's BeanPropertyWriter objects, but
 * holds them in fields typed as the CONCRETE final writer class. Jackson's own UnrolledBeanSerializer
 * is one shared class whose {@code _prop1.._prop6} fields are typed {@code BeanPropertyWriter}, so its
 * property call sites see every bean's writers and go megamorphic; here each call is statically bound
 * and the writer body (and the getter inside it) can be inlined. Class count stays O(beans):
 * one accessor-free writer class + one serializer class per bean.
 */
public final class PerBeanSerializers {

    private PerBeanSerializers() {}

    static final class ExtendedPersonSerializer extends ValueSerializer<Object> {
        private final PerBeanWriters.ExtendedPersonWriter p0, p1, p2, p3, p4;

        ExtendedPersonSerializer(List<BeanPropertyWriter> props) {
            p0 = (PerBeanWriters.ExtendedPersonWriter) props.get(0);
            p1 = (PerBeanWriters.ExtendedPersonWriter) props.get(1);
            p2 = (PerBeanWriters.ExtendedPersonWriter) props.get(2);
            p3 = (PerBeanWriters.ExtendedPersonWriter) props.get(3);
            p4 = (PerBeanWriters.ExtendedPersonWriter) props.get(4);
        }

        @Override
        public void serialize(Object bean, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeStartObject(bean);
            try {
                p0.serializeAsProperty(bean, gen, ctxt);
                p1.serializeAsProperty(bean, gen, ctxt);
                p2.serializeAsProperty(bean, gen, ctxt);
                p3.serializeAsProperty(bean, gen, ctxt);
                p4.serializeAsProperty(bean, gen, ctxt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            gen.writeEndObject();
        }
    }

    static final class AddressSerializer extends ValueSerializer<Object> {
        private final PerBeanWriters.AddressWriter p0, p1;

        AddressSerializer(List<BeanPropertyWriter> props) {
            p0 = (PerBeanWriters.AddressWriter) props.get(0);
            p1 = (PerBeanWriters.AddressWriter) props.get(1);
        }

        @Override
        public void serialize(Object bean, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeStartObject(bean);
            try {
                p0.serializeAsProperty(bean, gen, ctxt);
                p1.serializeAsProperty(bean, gen, ctxt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            gen.writeEndObject();
        }
    }

    static final class CarSerializer extends ValueSerializer<Object> {
        private final PerBeanWriters.CarWriter p0, p1;

        CarSerializer(List<BeanPropertyWriter> props) {
            p0 = (PerBeanWriters.CarWriter) props.get(0);
            p1 = (PerBeanWriters.CarWriter) props.get(1);
        }

        @Override
        public void serialize(Object bean, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeStartObject(bean);
            try {
                p0.serializeAsProperty(bean, gen, ctxt);
                p1.serializeAsProperty(bean, gen, ctxt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            gen.writeEndObject();
        }
    }

    /** Installs the per-bean writers (changeProperties) and then the per-bean serializer. */
    public static final class Modifier extends ValueSerializerModifier {
        private final Map<Class<?>, GeneratedPropertyAccessor> accessors;
        private final Map<Class<?>, List<BeanPropertyWriter>> generated = new HashMap<>();

        public Modifier(Map<Class<?>, GeneratedPropertyAccessor> accessors) {
            this.accessors = accessors;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                BeanDescription.Supplier beanDesc, List<BeanPropertyWriter> props) {
            Class<?> beanClass = beanDesc.getBeanClass();
            GeneratedPropertyAccessor accessor = accessors.get(beanClass);
            if (accessor == null) {
                return props;
            }
            List<BeanPropertyWriter> out = new ArrayList<>(props.size());
            for (BeanPropertyWriter w : props) {
                if (w.getClass() != BeanPropertyWriter.class || w.getMember() == null) {
                    return props; // anything unusual: keep Jackson's own shape
                }
                int index = accessor.indexOf(w.getMember().getName());
                if (index < 0) {
                    return props;
                }
                BeanPropertyWriter g;
                if (beanClass == ExtendedPerson.class) {
                    g = new PerBeanWriters.ExtendedPersonWriter(w, index);
                } else if (beanClass == Address.class) {
                    g = new PerBeanWriters.AddressWriter(w, index);
                } else if (beanClass == Car.class) {
                    g = new PerBeanWriters.CarWriter(w, index);
                } else {
                    return props;
                }
                out.add(g);
            }
            generated.put(beanClass, out);
            return out;
        }

        @Override
        public ValueSerializer<?> modifySerializer(SerializationConfig config,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            Class<?> beanClass = beanDesc.getBeanClass();
            List<BeanPropertyWriter> props = generated.get(beanClass);
            if (props == null) {
                return serializer;
            }
            if (beanClass == ExtendedPerson.class && props.size() == 5) {
                return new ExtendedPersonSerializer(props);
            }
            if (beanClass == Address.class && props.size() == 2) {
                return new AddressSerializer(props);
            }
            if (beanClass == Car.class && props.size() == 2) {
                return new CarSerializer(props);
            }
            return serializer;
        }
    }
}
