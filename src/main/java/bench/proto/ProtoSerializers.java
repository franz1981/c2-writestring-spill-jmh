package bench.proto;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import bench.model.Address;
import bench.model.Car;
import bench.model.Person;

/** Lookup-free serializers: pre-encoded names, nested serializers resolved once, no per-call config lookups. */
public final class ProtoSerializers {

    public static final class PersonSer extends StdSerializer<Person> implements ResolvableSerializer {
        private static final SerializedString F_FIRST = new SerializedString("firstName");
        private static final SerializedString F_FAMILY = new SerializedString("familyName");
        private static final SerializedString F_AGE = new SerializedString("age");
        private static final SerializedString F_ADDRESS = new SerializedString("address");
        private static final SerializedString F_CAR = new SerializedString("car");
        private JsonSerializer<Object> addressSer;
        private JsonSerializer<Object> carSer;

        public PersonSer() {
            super(Person.class);
        }

        @Override
        public void resolve(SerializerProvider provider) throws JsonMappingException {
            addressSer = provider.findValueSerializer(Address.class);
            carSer = provider.findValueSerializer(Car.class);
        }

        @Override
        public void serialize(Person v, JsonGenerator g, SerializerProvider prov) throws IOException {
            g.writeStartObject(v);
            g.writeFieldName(F_FIRST);
            g.writeString(v.getFirstName());
            g.writeFieldName(F_FAMILY);
            g.writeString(v.getLastName());
            g.writeFieldName(F_AGE);
            g.writeNumber(v.getAge());
            g.writeFieldName(F_ADDRESS);
            Address a = v.getAddress();
            if (a == null) {
                g.writeNull();
            } else {
                addressSer.serialize(a, g, prov);
            }
            g.writeFieldName(F_CAR);
            Car c = v.getCar();
            if (c == null) {
                g.writeNull();
            } else {
                carSer.serialize(c, g, prov);
            }
            g.writeEndObject();
        }
    }

    public static final class AddressSer extends StdSerializer<Address> {
        private static final SerializedString F_CITY = new SerializedString("city");
        private static final SerializedString F_STREET = new SerializedString("street");

        public AddressSer() {
            super(Address.class);
        }

        @Override
        public void serialize(Address v, JsonGenerator g, SerializerProvider prov) throws IOException {
            g.writeStartObject(v);
            g.writeFieldName(F_CITY);
            g.writeString(v.getCity());
            g.writeFieldName(F_STREET);
            g.writeString(v.getStreet());
            g.writeEndObject();
        }
    }

    public static final class CarSer extends StdSerializer<Car> {
        private static final SerializedString F_BRAND = new SerializedString("brand");
        private static final SerializedString F_MODEL = new SerializedString("model");

        public CarSer() {
            super(Car.class);
        }

        @Override
        public void serialize(Car v, JsonGenerator g, SerializerProvider prov) throws IOException {
            g.writeStartObject(v);
            g.writeFieldName(F_BRAND);
            g.writeString(v.getBrand());
            g.writeFieldName(F_MODEL);
            g.writeString(v.getModel());
            g.writeEndObject();
        }
    }
}
