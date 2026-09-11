package bench.paths.accessor;

import bench.paths.beans.*;

/**
 * Transcribed from the class Quarkus generates ({@code ExtendedPerson$quarkusjacksonaccessor}, branch
 * perf/pb-accessor-on-main, javap -c): the same property order, kind table, string switch in
 * {@code indexOf} and {@code if (index == i)} chains in the getters. Setters are omitted (the
 * serialization path never calls them).
 */
public final class ExtendedPersonAccessor extends GeneratedPropertyAccessor {
    public ExtendedPersonAccessor(Class<?> beanClass) { super(beanClass); }
    @Override public int indexOf(String name) {
        int result = -1;
        switch (name) {
            case "getAddress": result = 0; break;
            case "getAge": result = 1; break;
            case "getCar": result = 2; break;
            case "getFirstName": result = 3; break;
            case "getLastName": result = 4; break;
            default: break;
        }
        return result;
    }
    @Override public int kindOf(int index) {
        int result = -1;
        if (index == 0) result = 6;
        if (index == 1) result = 2;
        if (index == 2) result = 6;
        if (index == 3) result = 1;
        if (index == 4) result = 1;
        return result;
    }
    @Override public String stringGetter(Object bean, int index) {
        ExtendedPerson p = (ExtendedPerson) bean;
        if (index == 3) return p.getFirstName();
        if (index == 4) return p.getLastName();
        throw new IllegalStateException("No generated accessor for the property of bench.paths.beans.ExtendedPerson");
    }
    @Override public int intGetter(Object bean, int index) {
        ExtendedPerson p = (ExtendedPerson) bean;
        if (index == 1) return p.getAge();
        throw new IllegalStateException("No generated accessor for the property of bench.paths.beans.ExtendedPerson");
    }
    @Override public Object objectGetter(Object bean, int index) {
        ExtendedPerson p = (ExtendedPerson) bean;
        if (index == 0) return p.getAddress();
        if (index == 2) return p.getCar();
        throw new IllegalStateException("No generated accessor for the property of bench.paths.beans.ExtendedPerson");
    }
}
