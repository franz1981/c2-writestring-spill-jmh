package bench;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import bench.model.Address;
import bench.model.Car;
import bench.model.Person;

/** The payload: 20 references to one Person, serialized as a List<Person> (3021 bytes of JSON). */
public final class Fixtures {

    public static final TypeReference<List<Person>> LIST_OF_PERSON = new TypeReference<List<Person>>() {
    };

    private Fixtures() {
    }

    public static Person person() {
        Person p = new Person("Mario", "Fusco", 52, new Address("Gorgonzola", "Via Mattei 73"));
        p.setCar(new Car("Porsche", "Macan"));
        return p;
    }

    public static List<Person> people() {
        return Collections.nCopies(20, person());
    }
}
