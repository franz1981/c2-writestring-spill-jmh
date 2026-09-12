package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * {@link ExtendedPersonSer} with the three per-call lookups hoisted into {@code resolve}: the inclusion,
 * the property names (naming strategy applied once) and the nested beans' serializers. Everything else is
 * unchanged, including the out-of-line {@code serializeContent} and the out-of-line nested-bean helper, so
 * the compile-unit shape is the same as the application's and only the lookups differ.
 */
public final class ExtendedPersonSerResolved extends GeneratedSerResolved {

    private SerializableString nAddress, nAge, nCar, nFirstName, nFamilyName;
    private ValueSerializer<Object> addressSer;
    private ValueSerializer<Object> carSer;

    public ExtendedPersonSerResolved() {
        super(ExtendedPerson.class);
    }

    @Override
    protected void resolveProperties(SerializationContext ctxt) {
        nAddress = name(ctxt, "address", SerializedStrings.address);
        nAge = name(ctxt, "age", SerializedStrings.age);
        nCar = name(ctxt, "car", SerializedStrings.car);
        nFirstName = name(ctxt, "firstName", SerializedStrings.firstName);
        // familyName carries an explicit @JsonProperty, so the naming strategy never applies
        nFamilyName = SerializedStrings.familyName;
        addressSer = ctxt.findTypedValueSerializer(Address.class, true);
        carSer = ctxt.findTypedValueSerializer(Car.class, true);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        ExtendedPerson bean = (ExtendedPerson) value;

        Address address = bean.getAddress();
        if (includeAll || include.shouldSerialize(address)) {
            gen.writeName(nAddress);
            MapperUtil.serializePojo(addressSer, Address.class, address, bean, gen, ctxt);
        }

        int age = bean.getAge();
        if (includeAll || include.shouldSerialize(age)) {
            gen.writeName(nAge);
            gen.writeNumber(age);
        }

        Car car = bean.getCar();
        if (includeAll || include.shouldSerialize(car)) {
            gen.writeName(nCar);
            MapperUtil.serializePojo(carSer, Car.class, car, bean, gen, ctxt);
        }

        String firstName = bean.getFirstName();
        if (includeAll || include.shouldSerialize(firstName)) {
            gen.writeName(nFirstName);
            gen.writeString(firstName);
        }

        String lastName = bean.getLastName();
        if (includeAll || include.shouldSerialize(lastName)) {
            gen.writeName(nFamilyName);
            gen.writeString(lastName);
        }
    }
}
