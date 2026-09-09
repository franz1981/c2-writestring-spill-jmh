package bench.paths.sers;

import tools.jackson.core.io.SerializedString;

/**
 * Copy of the {@code SerializedStrings$quarkusjacksonserializer} class Quarkus generates: one
 * holder for the whole application, with a static field per distinct property name, shared by every
 * generated serializer.
 */
public final class SerializedStrings {

    static final SerializedString firstName = new SerializedString("firstName");
    static final SerializedString familyName = new SerializedString("familyName");
    static final SerializedString address = new SerializedString("address");
    static final SerializedString car = new SerializedString("car");
    static final SerializedString city = new SerializedString("city");
    static final SerializedString street = new SerializedString("street");
    static final SerializedString brand = new SerializedString("brand");
    static final SerializedString model = new SerializedString("model");
    static final SerializedString age = new SerializedString("age");

    private SerializedStrings() {}
}
