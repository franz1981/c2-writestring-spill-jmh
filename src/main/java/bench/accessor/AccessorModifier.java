package bench.accessor;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Mirrors Quarkus's {@code GeneratedPropertyWriterModifier}: swaps Jackson's reflective
 * {@link BeanPropertyWriter}s for accessor-backed ones. This is the sanctioned injection point, and
 * it is what makes Jackson's own {@code UnrolledBeanSerializer} drive our writers — which is the
 * inlining shape the Quarkus application actually runs.
 */
public final class AccessorModifier extends ValueSerializerModifier {

    private final Map<Class<?>, PropertyAccessor> accessors;
    private final Map<Class<?>, int[]> kinds;

    public AccessorModifier(Map<Class<?>, PropertyAccessor> accessors, Map<Class<?>, int[]> kinds) {
        this.accessors = accessors;
        this.kinds = kinds;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BeanDescription.Supplier beanDescRef, List<BeanPropertyWriter> beanProperties) {
        Class<?> beanClass = beanDescRef.getBeanClass();
        PropertyAccessor accessor = accessors.get(beanClass);
        if (accessor == null) {
            return beanProperties;
        }
        int[] kind = kinds.get(beanClass);
        for (int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            if (writer.getClass() != BeanPropertyWriter.class) {
                // specialised writers keep their behaviour, as in Quarkus
                continue;
            }
            AnnotatedMember member = writer.getMember();
            if (member == null) {
                continue;
            }
            int index = accessor.indexOf(member.getName());
            if (index < 0) {
                continue;
            }
            beanProperties.set(i, AccessorWriters.create(writer, accessor, index, kind[index]));
        }
        return beanProperties;
    }
}
