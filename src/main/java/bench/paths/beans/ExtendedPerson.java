package bench.paths.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The bean the application serves from {@code persons/get-all-extended}, copied field for field:
 * two Strings, an int, and two nested beans holding two Strings each - <b>six String properties in
 * total</b>, spread over three generated serializers.
 *
 * <p>{@code lastName} carries {@code @JsonProperty("familyName")} exactly as the original does.
 */
public class ExtendedPerson {

    private String firstName;

    @JsonProperty("familyName")
    private String lastName;

    private int age;

    private Address address;

    private Car car;

    public ExtendedPerson() {}

    public ExtendedPerson(String firstName, String lastName, int age, Address address, Car car) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.address = address;
        this.car = car;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public int getAge() { return age; }
    public Address getAddress() { return address; }
    public Car getCar() { return car; }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setAge(int age) { this.age = age; }
    public void setAddress(Address address) { this.address = address; }
    public void setCar(Car car) { this.car = car; }
}
