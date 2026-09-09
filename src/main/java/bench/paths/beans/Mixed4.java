package bench.paths.beans;

/**
 * The shape Quarkus's {@code Person} has: two Strings, an int and a double.
 *
 * <p>{@link Str5} isolates the number of inlined {@code writeString} copies with nothing between
 * them. This bean is here for the opposite reason: it puts a {@code double} between the String
 * writes, so {@code DoubleToDecimal} gets inlined into the same compiled method as the copy loops.
 * In the Quarkus app that is what sits between the two copies, and the copy after it is the one
 * that spills.
 */
public class Mixed4 {
    public String firstName = "Gorgonzola";
    public String lastName = "Cheese";
    public int age = 30;
    public double height = 1.83;
}
