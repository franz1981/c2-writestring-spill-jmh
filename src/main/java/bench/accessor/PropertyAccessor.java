package bench.accessor;

/**
 * Mirrors Quarkus's {@code GeneratedPropertyAccessor}: one class per bean, typed getters dispatching
 * on a property index. Generated with Gizmo in Quarkus, hand-written here so the shape — and
 * therefore the inlining — is the same.
 */
public abstract class PropertyAccessor {

    public String stringGetter(Object bean, int index) {
        throw new UnsupportedOperationException();
    }

    public int intGetter(Object bean, int index) {
        throw new UnsupportedOperationException();
    }

    public boolean booleanGetter(Object bean, int index) {
        throw new UnsupportedOperationException();
    }

    /** -1 when this accessor does not handle the property, as the generated one does. */
    public abstract int indexOf(String memberName);
}
