package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;

import bench.paths.beans.Address;

/** Hand-written copy of {@code Address$quarkusjacksonserializer}. */
public final class AddressSer extends GeneratedSer {

    public AddressSer() {
        super(Address.class);
    }

    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Address bean = (Address) value;
        SerializationInclude include = SerializationInclude.decode(value, ctxt);
        PropertyNamingStrategy strategy = ctxt.getConfig().getPropertyNamingStrategy();
        ctxt.getActiveView();

        String city = bean.getCity();
        if (include.shouldSerialize(city)) {
            SerializedString name = SerializedStrings.city;
            MapperUtil.writeFieldName(gen, strategy, "city", (SerializableString) name);
            gen.writeString(city);
        }

        String street = bean.getStreet();
        if (include.shouldSerialize(street)) {
            SerializedString name = SerializedStrings.street;
            MapperUtil.writeFieldName(gen, strategy, "street", (SerializableString) name);
            gen.writeString(street);
        }
    }
}
