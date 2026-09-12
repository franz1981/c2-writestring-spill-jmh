package bench.paths;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectWriter;
import tools.jackson.module.afterburner.AfterburnerModule;

import org.openjdk.jmh.annotations.*;

import bench.Sink;
import bench.paths.beans.Address;
import bench.paths.beans.Car;
import bench.paths.beans.ExtendedPerson;

/**
 * Jackson's own Afterburner module (3.1.4): a ByteBuddy-generated BeanPropertyAccessor subclass per
 * bean with stringGetter(bean, index) etc., and shared typed writers (StringMethodPropertyWriter,
 * IntMethodPropertyWriter, ObjectMethodPropertyWriter) over Jackson's UnrolledBeanSerializer - the
 * runtime-generated twin of the Quarkus per-bean accessor design in AccessorBench (writers=typed).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "-Xms1g", "-Xmx1g", "-XX:+UseParallelGC", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class AfterburnerBench {

    @Param("0")
    public int strLen;

    private ObjectWriter writer;
    private List<ExtendedPerson> people;
    private Sink out;

    @Setup
    public void setup() {
        writer = QuarkusMapper.listWriter(QuarkusMapper.builder().addModule(new AfterburnerModule()).build());
        ExtendedPerson person;
        if (strLen > 0) {
            String filler = "x".repeat(strLen);
            person = new ExtendedPerson(filler, filler, 30, new Address(filler, filler), new Car(filler, filler));
        } else {
            person = new ExtendedPerson("John", "Doe", 30, new Address("Gotham", "123 Main St"), new Car("Toyota", "Camry"));
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
}
