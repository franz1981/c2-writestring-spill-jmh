package bench.paths.accessor;

import bench.paths.beans.*;

/**
 * Transcribed from the class Quarkus generates ({@code Address$quarkusjacksonaccessor}, branch
 * perf/pb-accessor-on-main, javap -c): the same property order, kind table, string switch in
 * {@code indexOf} and {@code if (index == i)} chains in the getters. Setters are omitted (the
 * serialization path never calls them).
 */
public final class AddressAccessor extends GeneratedPropertyAccessor {
    public AddressAccessor(Class<?> beanClass) { super(beanClass); }
    @Override public int indexOf(String name) {
        int result = -1;
        switch (name) {
            case "getCity": result = 0; break;
            case "getStreet": result = 1; break;
            default: break;
        }
        return result;
    }
    @Override public int kindOf(int index) {
        int result = -1;
        if (index == 0) result = 1;
        if (index == 1) result = 1;
        return result;
    }
    @Override public String stringGetter(Object bean, int index) {
        Address p = (Address) bean;
        if (index == 0) return p.getCity();
        if (index == 1) return p.getStreet();
        throw new IllegalStateException("No generated accessor for the property of bench.paths.beans.Address");
    }
}
