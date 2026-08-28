package bench.flat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;

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

/**
 * Serializes a {@code List<Flat>} with a hand-written serializer making one {@code writeString} call per
 * element. Run it twice: once as is, and once with
 * {@code -XX:CompileCommand=dontinline,com/fasterxml/jackson/core/json/UTF8JsonGenerator.writeString}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class SingleBench {

    @Param("10")
    public int size;

    private ObjectWriter writer;
    private List<Flat> values;
    private Sink out;

    @Setup
    public void setup() {
        SimpleModule module = new SimpleModule("flat");
        module.addSerializer(Flat.class, new FlatSer());
        writer = new ObjectMapper().registerModule(module).writer()
                .forType(new TypeReference<List<Flat>>() {
                });
        values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(new Flat());
        }
        out = new Sink(64 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    /** writeString is inlined into the serializer: the copy loop spills. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long serialize() throws IOException {
        out.reset();
        writer.writeValue(out, values);
        return out.count();
    }

    /** Identical work, with writeString kept out of the serializer: the copy loop keeps its registers. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Fork(value = 3, jvmArgsAppend = "-XX:CompileCommand=dontinline,"
            + "com/fasterxml/jackson/core/json/UTF8JsonGenerator.writeString")
    public long serializeWriteStringNotInlined() throws IOException {
        out.reset();
        writer.writeValue(out, values);
        return out.count();
    }
}
