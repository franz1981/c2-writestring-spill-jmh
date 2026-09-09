package bench.paths;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
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
import bench.paths.beans.GenPerson;
import bench.paths.sers.OrderProbeSers;

/**
 * Varies what sits between the two {@code writeString} calls, to find what the register allocator
 * reacts to. One variant per {@code order} value - see {@link OrderProbeSers}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch" })
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Threads(1)
public class OrderProbeBench {

    @Param("20")
    public int size;

    @Param("256")
    public int len;

    /** A=generated order, B=strings only, C=strings first, D=numbers first, E=int between, F=double between. */
    @Param({ "A", "B", "C", "D", "E", "F" })
    public String order;

    private ObjectWriter writer;
    private List<GenPerson> people;
    private Sink out;

    @SuppressWarnings("unchecked")
    private static ValueSerializer<GenPerson> serializerFor(String order) {
        switch (order) {
            case "A": return new OrderProbeSers.A();
            case "B": return new OrderProbeSers.B();
            case "C": return new OrderProbeSers.C();
            case "D": return new OrderProbeSers.D();
            case "E": return new OrderProbeSers.E();
            case "F": return new OrderProbeSers.F();
            default: throw new IllegalArgumentException(order);
        }
    }

    @Setup
    public void setup() {
        SimpleModule module = new SimpleModule("probe");
        module.addSerializer(GenPerson.class, serializerFor(order));
        writer = JsonMapper.builder().addModule(module).build().writer()
                .forType(new TypeReference<List<GenPerson>>() {
                });
        String lastName = "b" + "x".repeat(Math.max(0, len - 1));
        people = Collections.nCopies(size, new GenPerson("John", lastName, 30, 1.75));
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
