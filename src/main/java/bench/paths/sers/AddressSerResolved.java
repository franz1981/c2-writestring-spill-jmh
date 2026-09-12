package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.SerializationContext;

import bench.paths.beans.Address;

/** {@link AddressSer} with the inclusion and the property names resolved once. */
public final class AddressSerResolved extends GeneratedSerResolved {

    private SerializableString nCity;
    private SerializableString nStreet;

    public AddressSerResolved() {
        super(Address.class);
    }

    @Override
    protected void resolveProperties(SerializationContext ctxt) {
        nCity = name(ctxt, "city", SerializedStrings.city);
        nStreet = name(ctxt, "street", SerializedStrings.street);
    }

    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Address bean = (Address) value;

        String city = bean.getCity();
        if (includeAll || include.shouldSerialize(city)) {
            gen.writeName(nCity);
            gen.writeString(city);
        }

        String street = bean.getStreet();
        if (includeAll || include.shouldSerialize(street)) {
            gen.writeName(nStreet);
            gen.writeString(street);
        }
    }
}
