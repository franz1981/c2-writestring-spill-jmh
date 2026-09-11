package bench.paths.accessor;

/**
 * Base class of the build-time generated property accessor. A single subclass is generated for the whole
 * application; each bean gets one instance of it, identified by a {@code classId}, and every getter and setter
 * dispatches on that id to a per-bean method holding the property index switch. Jackson keeps ownership of its
 * {@code BeanSerializer} and {@code BeanDeserializer}: only the reflective property access of their
 * {@code BeanPropertyWriter}s and {@code SettableBeanProperty}s is replaced (see {@link GeneratedPropertyWriters} and
 * {@link GeneratedSettableProperties}).
 */
public abstract class GeneratedPropertyAccessor {

    public static final int KIND_STRING = 1;
    public static final int KIND_INT = 2;
    public static final int KIND_LONG = 3;
    public static final int KIND_BOOLEAN = 4;
    public static final int KIND_DOUBLE = 5;
    public static final int KIND_OBJECT = 6;

    private final Class<?> beanClass;

    protected GeneratedPropertyAccessor(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    public final Class<?> beanClass() {
        return beanClass;
    }

    /**
     * @return the index of the property read through the given Java member (getter method or public field name), or -1
     */
    public int indexOf(String memberName) {
        return -1;
    }

    /**
     * @return one of the {@code KIND_*} constants for the property at the given index
     */
    public abstract int kindOf(int index);

    public String stringGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public int intGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public long longGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public boolean booleanGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public double doubleGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public Object objectGetter(Object bean, int index) {
        throw unsupported(index);
    }

    public void stringSetter(Object bean, int index, String value) {
        throw unsupported(index);
    }

    public void intSetter(Object bean, int index, int value) {
        throw unsupported(index);
    }

    public void longSetter(Object bean, int index, long value) {
        throw unsupported(index);
    }

    public void booleanSetter(Object bean, int index, boolean value) {
        throw unsupported(index);
    }

    public void doubleSetter(Object bean, int index, double value) {
        throw unsupported(index);
    }

    public void objectSetter(Object bean, int index, Object value) {
        throw unsupported(index);
    }

    /**
     * @return the index of the property written through the given Java member (setter method or public field name),
     *         or -1 when the generator did not cover it
     */
    public int indexOfSetter(String memberName) {
        return -1;
    }

    protected final IllegalStateException unsupported(int index) {
        return new IllegalStateException("No generated accessor for property " + index + " of " + beanClass.getName());
    }
}
