package bench.paths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import org.quarkus.metaprogramming.Person;

import bench.Sink;

/**
 * The REAL Quarkus generated serializer, not an imitation: {@code Person$quarkusjacksonserializer}
 * and {@code Person} are the classes dumped out of the built Quarkus app, together with the
 * {@code JacksonMapperUtil} / {@code GeneratedSerializer} runtime they call into.
 *
 * <p>Person's shape is String, String, int, double, and the generator emits the writes in the order
 * age, firstName, height, lastName - so the double's formatting lands between the two String writes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class GenSerBench {

    @Param("20")
    public int size;

    /** len &lt;= 0 means: use the Quarkus app's exact values, new Person("John", "Doe", 30, 1.75). */
    @Param({ "0", "10", "40" })
    public int len;

    private ObjectWriter writer;
    private ObjectWriter reflectionWriter;
    private List<Person> people;
    private Sink out;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() throws Exception {
        Class<?> serClass = Class.forName("org.quarkus.metaprogramming.Person$quarkusjacksonserializer");
        ValueSerializer<Person> ser =
                (ValueSerializer<Person>) serClass.getDeclaredConstructor().newInstance();
        SimpleModule m = new SimpleModule("gen");
        m.addSerializer(Person.class, ser);
        writer = JsonMapper.builder().addModule(m).build().writer()
                .forType(new TypeReference<List<Person>>() {});
        // no module: Jackson's own reflective BeanSerializer, the reflection-free=false arm
        reflectionWriter = JsonMapper.builder().build().writer()
                .forType(new TypeReference<List<Person>>() {});
        if (len <= 0) {
            // exactly what PersonResource serves:
            //   DEFAULT_PERSON  = new Person("John", "Doe", 30, 1.75)
            //   DEFAULT_PERSONS = Collections.nCopies(20, DEFAULT_PERSON)
            // Same instance repeated, and a CopiesList rather than an ArrayList - Jackson's
            // CollectionSerializer iterates it differently.
            people = Collections.nCopies(size, new Person("John", "Doe", 30, 1.75));
        } else {
            // Asymmetric on purpose: firstName keeps the app's short value, so the copy loop
            // C2 allocates cleanly stays negligible; only lastName - whose inlined writeString
            // copy is the one that spills its induction variable - grows to len chars.
            // List shape and the numeric fields stay exactly as the app serves them.
            String lastName = "b" + "x".repeat(Math.max(0, len - 1));
            people = Collections.nCopies(size, new Person("John", lastName, 30, 1.75));
        }
        out = new Sink(1024 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    /** Quarkus generated serializer (reflection-free ON). */
    @Benchmark
    public long genser() {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }

    /** Jackson's own reflective BeanSerializer (reflection-free OFF). */
    @Benchmark
    public long reflection() {
        out.reset();
        reflectionWriter.writeValue(out, people);
        return out.count();
    }
}
