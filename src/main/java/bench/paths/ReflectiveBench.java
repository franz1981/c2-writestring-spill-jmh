package bench.paths;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectWriter;

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

/**
 * The same data as {@link ExtendedPersonBench}, written by Jackson's own reflective bean
 * serializers - what the application does with
 * {@code quarkus.rest.jackson.optimization.enable-reflection-free-serializers=false}.
 *
 * <p>Nothing is registered: the mapper is {@link QuarkusMapper#builder()} as is, so Jackson picks
 * {@code UnrolledBeanSerializer} (six properties or fewer) with {@code BeanPropertyWriter}s reading
 * the getters through {@code MethodHandle}s. No {@code @CompilerControl} on any Jackson method:
 * the compile roots are whatever C2 decides, to be compared with the application's.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class ReflectiveBench {

    /** Same meaning as in {@link ExtendedPersonBench}. */
    @Param("0")
    public int strLen;

    private ObjectWriter writer;
    private List<ExtendedPerson> people;
    private Sink out;

    @Setup
    public void setup() {
        writer = QuarkusMapper.listWriter(QuarkusMapper.builder().build());
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

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long serialize() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }

    /** Control: writeString kept out of line everywhere. */
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
