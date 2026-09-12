package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * {@link ExtendedPersonSer} with EXACTLY one change: the nested beans' serializers are resolved once in
 * {@code resolve} instead of by class on every write. This is the serialization half of the fix that
 * the 3.39.1 branch perf/reflection-free-jackson-fixes-only carries and main does not. Everything else -
 * the per-call inclusion decode, the per-call naming strategy, writeFieldName with its static final
 * SerializedString, the discarded getActiveView - is left as main emits it.
 */
public final class ExtendedPersonSerNested extends GeneratedSer {

    private ValueSerializer<Object> addressSer;
    private ValueSerializer<Object> carSer;

    @Override
    public void resolve(SerializationContext ctxt) {
        addressSer = ctxt.findTypedValueSerializer(Address.class, true);
        carSer = ctxt.findTypedValueSerializer(Car.class, true);
    }


    public ExtendedPersonSerNested() {
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
            MapperUtil.serializePojo(addressSer, Address.class, address, bean, gen, ctxt);
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
            MapperUtil.serializePojo(carSer, Car.class, car, bean, gen, ctxt);
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
