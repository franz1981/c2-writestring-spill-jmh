package bench.paths;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.module.SimpleModule;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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

import bench.Sink;
import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;
import bench.paths.sers.AddressSer;
import bench.paths.sers.CarSer;
import bench.paths.sers.ExtendedPersonSer;
import bench.paths.sers.AddressSerResolved;
import bench.paths.sers.CarSerResolved;
import bench.paths.sers.ExtendedPersonSerNested;
import bench.paths.sers.ExtendedPersonSerResolved;

/**
 * Problem 3, reproducing what the Quarkus application serves from {@code persons/get-all-extended}:
 * {@code Collections.nCopies(20, EXTENDED_DEFAULT_PERSON)} written by the serializers Quarkus
 * generates for {@code ExtendedPerson}, {@code Address} and {@code Car}.
 *
 * <p>Six String properties over three serializers. Two {@code writeString} call sites are in
 * {@code ExtendedPersonSer}; the other four are reached through {@code MapperUtil.serializePojo},
 * which is where the nested serializers get inlined - matching the application, where that method
 * holds four copy loops and {@code serializeContent} holds two.
 *
 * <p>The data is declared exactly as {@code PersonResource} declares it - {@code static final}
 * fields holding {@code Collections.nCopies(20, EXTENDED_DEFAULT_PERSON)} - so that C2 gets the same
 * constants the application gives it.
 *
 * <p>Run both benchmarks: {@code serialize} has {@code writeString} inlined and is the case to fix,
 * {@code serializeWriteStringNotInlined} is the control.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
/*
 * Same JVM flags as the application benchmark (run.sh): 1 GB heap, ParallelGC.
 * The collector matters: G1 enables loop strip mining by default, ParallelGC does not, and
 * without it C2 allocates the copy loops differently (buffer, character and output pointer
 * parked in XMM registers - the loops the application compiles). See README.
 */
@Fork(value = 3, jvmArgs = { "-Xms1g", "-Xmx1g", "-XX:+UseParallelGC", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class ExtendedPersonBench {


    /**
     * Not a good practice is made to match what the Quarkus metaprogramming benchmark does
     */
    /**
     * Length of every String property. {@code 0} keeps the application's own values; any other
     * value replaces all six with the <b>same</b> ASCII String instance of that length, so every
     * copy loop sees identical input and identical length and anything that differs between the six
     * is a compiler decision.
     */
    @Param("0")
    public int strLen;

    private ObjectWriter writer;
    private List<ExtendedPerson> people;
    private Sink out;

    /**
     * main = the generator as Quarkus main emits it; nested = main plus the nested serializer resolved
     * once; resolved = nested plus the inclusion and the property names resolved once.
     */
    @Param("main")
    public String gen;

    @Setup
    public void setup() {
        SimpleModule module = new SimpleModule("gen");
        if ("nested".equals(gen)) {
            module.addSerializer(ExtendedPerson.class, new ExtendedPersonSerNested());
            module.addSerializer(Address.class, new AddressSer());
            module.addSerializer(Car.class, new CarSer());
        } else if ("resolved".equals(gen)) {
            module.addSerializer(ExtendedPerson.class, new ExtendedPersonSerResolved());
            module.addSerializer(Address.class, new AddressSerResolved());
            module.addSerializer(Car.class, new CarSerResolved());
        } else {
            module.addSerializer(ExtendedPerson.class, new ExtendedPersonSer());
            module.addSerializer(Address.class, new AddressSer());
            module.addSerializer(Car.class, new CarSer());
        }
        // mapper and writer configured as the application configures them (see QuarkusMapper)
        writer = QuarkusMapper.listWriter(QuarkusMapper.builder().addModule(module).build());
        // held as PersonResource holds it: one instance, repeated, in a CopiesList
        ExtendedPerson person;
        if (strLen > 0) {
            String filler = "x".repeat(strLen);
            person = new ExtendedPerson(filler, filler, 30,
                    new Address(filler, filler),
                    new Car(filler, filler));
        } else {
            person = new ExtendedPerson("John", "Doe", 30,
                    new Address("Gotham", "123 Main St"),
                    new Car("Toyota", "Camry"));
        }
        people = Collections.nCopies(20, person);
        out = new Sink(1024 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    /** writeString is inlined into the serializers: six copies of the copy loop. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long serialize() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }

    /** Identical work, with writeString kept out of the serializers: one copy, shared. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Fork(value = 3, jvmArgs = { "-Xms1g", "-Xmx1g", "-XX:+UseParallelGC", "-XX:+AlwaysPreTouch",
            "-XX:CompileCommand=dontinline,tools/jackson/core/json/UTF8JsonGenerator.writeString" })
    public long serializeWriteStringNotInlined() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }
}
