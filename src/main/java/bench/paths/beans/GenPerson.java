package bench.paths.beans;

/**
 * The bean Quarkus generates a serializer for in the application these numbers came from:
 * {@code String firstName, String lastName, int age, double height}, accessed through getters.
 *
 * <p>{@code lastName} is settable in length because it is the property whose inlined
 * {@code writeString} copy gets the bad register allocation - see {@link bench.paths.sers.GenPersonSer}.
 */
public class GenPerson {

    private final String firstName;
    private final String lastName;
    private final int age;
    private final double height;

    public GenPerson(String firstName, String lastName, int age, double height) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.height = height;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public int getAge() { return age; }
    public double getHeight() { return height; }
}
