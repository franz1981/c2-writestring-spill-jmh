package bench;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import bench.model.Person;
import bench.proto.ProtoModules;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The same 3021 bytes of JSON, produced by the same code from the same {@code List<Person>}, compiled two ways.
 *
 * <ul>
 * <li>{@link #serializer()} - C2 inlines the whole serializer tree, including six copies of
 * {@code UTF8JsonGenerator._writeStringSegment}, into one ~50 KB nmethod. Every copy of the ASCII byte-copy
 * loop then carries 14-16 stack-slot operands per iteration.
 * <li>{@link #serializerNotInlinedWriteString()} - identical work, with
 * {@code UTF8JsonGenerator.writeString} excluded from inlining, so the loop is compiled once in its own
 * ~1.6 KB nmethod and keeps its values in registers.
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class WriteStringSpillBench {

    private ObjectWriter singleMethod;
    private List<Person> list;
    private Sink out;

    @Setup
    public void setup() throws IOException {
        singleMethod = new ObjectMapper().registerModule(ProtoModules.serializers())
                .writer().forType(Fixtures.LIST_OF_PERSON);
        list = Fixtures.people();
        out = new Sink(64 * 1024);

        byte[] json = bytes(singleMethod);
        if (json.length != 3021) {
            throw new AssertionError("expected 3021 bytes, got " + json.length);
        }
    }

    private byte[] bytes(ObjectWriter w) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        w.writeValue(sink, list);
        return sink.toByteArray();
    }

    /** The shape that spills. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long serializer() throws IOException {
        out.reset();
        singleMethod.writeValue(out, list);
        return out.count();
    }

    /** The spilling shape, with the inlining that produces the spill turned off. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Fork(value = 3, jvmArgsAppend = {
            "-XX:CompileCommand=dontinline,com/fasterxml/jackson/core/json/UTF8JsonGenerator.writeString" })
    public long serializerNotInlinedWriteString() throws IOException {
        out.reset();
        singleMethod.writeValue(out, list);
        return out.count();
    }
}
