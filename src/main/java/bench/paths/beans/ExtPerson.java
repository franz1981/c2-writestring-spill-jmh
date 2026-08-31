package bench.paths.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The bean the end-to-end benchmark serializes ({@code ExtendedPerson}): three scalars plus two
 * nested beans. The nesting matters - in the app the nested serializers are inlined into this bean's
 * frame, which is what makes it large.
 */
public class ExtPerson {
    public String firstName;

    @JsonProperty("familyName")
    public String lastName;

    public int age;

    public Address address;

    public Car car;

    public ExtPerson() {
    }

    public ExtPerson(String firstName, String lastName, int age, Address address, Car car) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.address = address;
        this.car = car;
    }
}
