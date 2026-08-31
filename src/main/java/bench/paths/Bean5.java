package bench.paths;

/**
 * Five String properties, matching the shape of the bean in the Quarkus benchmark
 * ({@code ExtendedPerson}: five properties, serialized through Jackson 3's
 * {@code UnrolledBeanSerializer}, which unrolls up to six).
 */
public class Bean5 {
    public String p0 = "Gorgonzola";
    public String p1 = "Cheese";
    public String p2 = "Milano";
    public String p3 = "Via Roma";
    public String p4 = "Fiat500";
}
