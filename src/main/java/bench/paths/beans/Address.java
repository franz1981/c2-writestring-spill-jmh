package bench.paths.beans;

/** Copy of the {@code Address} nested in the application's {@code ExtendedPerson}. */
public class Address {

    private String city;
    private String street;

    public Address() {}

    public Address(String city, String street) {
        this.city = city;
        this.street = street;
    }

    public String getCity() { return city; }
    public String getStreet() { return street; }
    public void setCity(String city) { this.city = city; }
    public void setStreet(String street) { this.street = street; }
}
