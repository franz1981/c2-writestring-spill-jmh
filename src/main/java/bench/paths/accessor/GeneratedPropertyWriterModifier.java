package bench.paths.accessor;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Replaces the {@link BeanPropertyWriter}s of the beans that have a generated {@link GeneratedPropertyAccessor} with
 * {@link GeneratedPropertyWriters}; properties the generator did not cover keep Jackson's own writer.
 */
public class GeneratedPropertyWriterModifier extends ValueSerializerModifier {

    private final Map<Class<?>, GeneratedPropertyAccessor> accessors;
    /** Experiment (JMH only): one KindWriter class for all kinds instead of the six typed writers. */
    private final boolean kindWriter;

    public GeneratedPropertyWriterModifier(Map<Class<?>, GeneratedPropertyAccessor> accessors) {
        this(accessors, false);
    }

    public GeneratedPropertyWriterModifier(Map<Class<?>, GeneratedPropertyAccessor> accessors, boolean kindWriter) {
        this.accessors = accessors;
        this.kindWriter = kindWriter;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties) {
        GeneratedPropertyAccessor accessor = accessors.get(beanDesc.getBeanClass());
        if (accessor == null) {
            return beanProperties;
        }
        for (int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            if (writer.getClass() != BeanPropertyWriter.class) {
                // specialised writers (e.g. UnwrappingBeanPropertyWriter for @JsonUnwrapped) keep their behaviour
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
            int kind = accessor.kindOf(index);
            if (!kindMatches(kind, writer.getType().getRawClass())) {
                continue;
            }
            beanProperties.set(i, kindWriter ? new KindWriter(writer, accessor, index, kind)
                    : GeneratedPropertyWriters.create(writer, accessor, index, kind));
        }
        return beanProperties;
    }

    private static boolean kindMatches(int kind, Class<?> rawType) {
        return switch (kind) {
            case GeneratedPropertyAccessor.KIND_STRING -> rawType == String.class;
            case GeneratedPropertyAccessor.KIND_INT -> rawType == int.class;
            case GeneratedPropertyAccessor.KIND_LONG -> rawType == long.class;
            case GeneratedPropertyAccessor.KIND_BOOLEAN -> rawType == boolean.class;
            case GeneratedPropertyAccessor.KIND_DOUBLE -> rawType == double.class;
            case GeneratedPropertyAccessor.KIND_OBJECT -> !rawType.isPrimitive();
            default -> false;
        };
    }
}
