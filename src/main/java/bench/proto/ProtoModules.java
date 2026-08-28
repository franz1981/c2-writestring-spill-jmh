package bench.proto;

import com.fasterxml.jackson.databind.module.SimpleModule;
import bench.model.Address;
import bench.model.Car;
import bench.model.Person;

/** The serializer shape under test: one hand-written {@code serialize()} body per bean. */
public final class ProtoModules {

    private ProtoModules() {
    }

    public static SimpleModule serializers() {
        SimpleModule module = new SimpleModule("proto-ser");
        module.addSerializer(Person.class, new ProtoSerializers.PersonSer());
        module.addSerializer(Address.class, new ProtoSerializers.AddressSer());
        module.addSerializer(Car.class, new ProtoSerializers.CarSer());
        return module;
    }
}
