package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.SerializationContext;

import bench.paths.beans.Car;

/** {@link CarSer} with the inclusion and the property names resolved once. */
public final class CarSerResolved extends GeneratedSerResolved {

    private SerializableString nBrand;
    private SerializableString nModel;

    public CarSerResolved() {
        super(Car.class);
    }

    @Override
    protected void resolveProperties(SerializationContext ctxt) {
        nBrand = name(ctxt, "brand", SerializedStrings.brand);
        nModel = name(ctxt, "model", SerializedStrings.model);
    }

    @Override
    public void serializeContent(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Car bean = (Car) value;

        String brand = bean.getBrand();
        if (includeAll || include.shouldSerialize(brand)) {
            gen.writeName(nBrand);
            gen.writeString(brand);
        }

        String model = bean.getModel();
        if (includeAll || include.shouldSerialize(model)) {
            gen.writeName(nModel);
            gen.writeString(model);
        }
    }
}
