package bench.paths;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
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
 * <p>Values are the application's own: {@code EXTENDED_DEFAULT_PERSON} as {@code PersonResource}
 * declares it.
 *
 * <p>Run both benchmarks: {@code serialize} has {@code writeString} inlined and is the case to fix,
 * {@code serializeWriteStringNotInlined} is the control.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class ExtendedPersonBench {

    @Param("20")
    public int size;


    private ObjectWriter writer;
    private List<ExtendedPerson> people;
    private Sink out;

    @Setup
    public void setup() {
        SimpleModule module = new SimpleModule("gen");
        module.addSerializer(ExtendedPerson.class, new ExtendedPersonSer());
        module.addSerializer(Address.class, new AddressSer());
        module.addSerializer(Car.class, new CarSer());
        writer = JsonMapper.builder().addModule(module).build().writer()
                .forType(new TypeReference<List<ExtendedPerson>>() {
                });
        // the application serves Collections.nCopies(20, EXTENDED_DEFAULT_PERSON) - one instance
        // repeated, and a CopiesList rather than an ArrayList
        ExtendedPerson person = new ExtendedPerson("John", "Doe", 30,
                new Address("Gotham", "123 Main St"),
                new Car("Toyota", "Camry"));
        people = Collections.nCopies(size, person);
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
    @Fork(value = 3, jvmArgs = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch",
            "-XX:CompileCommand=dontinline,tools/jackson/core/json/UTF8JsonGenerator.writeString" })
    public long serializeWriteStringNotInlined() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }
}
