package bench.paths.beans;

/** Nested bean, as in the end-to-end benchmark. */
public class Address {
    public String city;
    public String street;

    public Address() {
    }

    public Address(String city, String street) {
        this.city = city;
        this.street = street;
    }
}
