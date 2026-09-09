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
import bench.paths.beans.GenPerson;
import bench.paths.sers.GenPersonSer;

/**
 * Problem 3: a serializer with two String properties gets two inlined copies of the
 * {@code writeString} copy loop, and C2 allocates only the first one well.
 *
 * <p>{@code firstName} is fixed at the application's own {@code "John"}, so its copy - the one that
 * keeps its registers - stays negligible. Only {@code lastName}, whose copy carries its loop
 * counter on the stack, grows with {@code len}.
 *
 * <p>Run both benchmarks: {@code serialize} has {@code writeString} inlined and is the case to fix,
 * {@code serializeWriteStringNotInlined} is the control.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
// LoopMaxUnroll=2 matches what the application's compiled loop does. Left to itself C2
// unrolls this loop 4x here, which is a different compiled shape from the one being studied.
@Fork(value = 5, jvmArgsAppend = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:LoopMaxUnroll=2" })
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Threads(1)
public class GenShapeBench {

    @Param("20")
    public int size;

    /** Length of {@code lastName}. {@code firstName} is always {@code "John"}. */
    @Param({ "3", "256" })
    public int len;

    private ObjectWriter writer;
    private List<GenPerson> people;
    private Sink out;

    @Setup
    public void setup() {
        SimpleModule module = new SimpleModule("gen");
        module.addSerializer(GenPerson.class, new GenPersonSer());
        writer = JsonMapper.builder().addModule(module).build().writer()
                .forType(new TypeReference<List<GenPerson>>() {
                });
        String lastName = len == 3 ? "Doe" : "b" + "x".repeat(Math.max(0, len - 1));
        // the application serves Collections.nCopies(20, DEFAULT_PERSON) - same instance repeated,
        // and a CopiesList rather than an ArrayList
        people = Collections.nCopies(size, new GenPerson("John", lastName, 30, 1.75));
        out = new Sink(1024 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    /** writeString is inlined into the serializer: the second copy spills. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long serialize() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }

    /** Identical work, with writeString kept out of the serializer: one copy, no second allocation. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Fork(value = 5, jvmArgsAppend = { "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:LoopMaxUnroll=2",
            "-XX:CompileCommand=dontinline,tools/jackson/core/json/UTF8JsonGenerator.writeString" })
    public long serializeWriteStringNotInlined() throws IOException {
        out.reset();
        writer.writeValue(out, people);
        return out.count();
    }
}
