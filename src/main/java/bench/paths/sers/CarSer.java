package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationContext;

import bench.paths.beans.Car;

/** Hand-written copy of {@code Car$quarkusjacksonserializer}. */
public final class CarSer extends GeneratedSer {

    public CarSer() {
        super(Car.class);
    }

    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Car bean = (Car) value;
        SerializationInclude include = SerializationInclude.decode(value, ctxt);
        PropertyNamingStrategy strategy = ctxt.getConfig().getPropertyNamingStrategy();
        ctxt.getActiveView();

        String brand = bean.getBrand();
        if (include.shouldSerialize(brand)) {
            SerializedString name = SerializedStrings.brand;
            MapperUtil.writeFieldName(gen, strategy, "brand", (SerializableString) name);
            gen.writeString(brand);
        }

        String model = bean.getModel();
        if (include.shouldSerialize(model)) {
            SerializedString name = SerializedStrings.model;
            MapperUtil.writeFieldName(gen, strategy, "model", (SerializableString) name);
            gen.writeString(model);
        }
    }
}
