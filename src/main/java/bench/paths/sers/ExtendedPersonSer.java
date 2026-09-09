package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * Hand-written copy of {@code ExtendedPerson$quarkusjacksonserializer}, transcribed from the
 * bytecode Quarkus generates for the application. Properties in the order the generator emits them
 * (alphabetical by JSON name: address, age, car, firstName, familyName), a {@code shouldSerialize}
 * guard per property, nested beans through {@code serializePojo}, and {@code familyName} written
 * with {@code writeName} directly because its explicit {@code @JsonProperty} bypasses the naming
 * strategy.
 *
 * <p>Two {@code writeString} call sites are here; the other four are reached through
 * {@link MapperUtil#serializePojo}.
 */
public final class ExtendedPersonSer extends GeneratedSer {

    public ExtendedPersonSer() {
        super(ExtendedPerson.class);
    }

    /**
     * Not inlined, matching the application: in its profile this method and
     * {@link MapperUtil#serializePojo} are the only two physical frames - everything else
     * ({@code GeneratedSer.serialize}, the nested serializers, {@code writeString},
     * {@code _writeStringSegment}) is inlined into one of them.
     */
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        ExtendedPerson bean = (ExtendedPerson) value;
        SerializationInclude include = SerializationInclude.decode(value, ctxt);
        PropertyNamingStrategy strategy = ctxt.getConfig().getPropertyNamingStrategy();
        ctxt.getActiveView();

        Address address = bean.getAddress();
        if (include.shouldSerialize(address)) {
            SerializedString name = SerializedStrings.address;
            MapperUtil.writeFieldName(gen, strategy, "address", (SerializableString) name);
            MapperUtil.serializePojo(address, bean, gen, ctxt);
        }

        int age = bean.getAge();
        if (include.shouldSerialize(age)) {
            SerializedString name = SerializedStrings.age;
            MapperUtil.writeFieldName(gen, strategy, "age", (SerializableString) name);
            gen.writeNumber(age);
        }

        Car car = bean.getCar();
        if (include.shouldSerialize(car)) {
            SerializedString name = SerializedStrings.car;
            MapperUtil.writeFieldName(gen, strategy, "car", (SerializableString) name);
            MapperUtil.serializePojo(car, bean, gen, ctxt);
        }

        String firstName = bean.getFirstName();
        if (include.shouldSerialize(firstName)) {
            SerializedString name = SerializedStrings.firstName;
            MapperUtil.writeFieldName(gen, strategy, "firstName", (SerializableString) name);
            gen.writeString(firstName);
        }

        String lastName = bean.getLastName();
        if (include.shouldSerialize(lastName)) {
            SerializedString name = SerializedStrings.familyName;
            gen.writeName((SerializableString) name);
            gen.writeString(lastName);
        }
    }
}
