package bench.paths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

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

import bench.Sink;

/**
 * Which write paths suffer from the inlining/spilling problem, on Jackson 3?
 *
 * <p>Two serializers do <em>identical</em> work on the same five-String bean. They differ only in
 * whether the per-property writer is inlined into the serializer:
 * <ul>
 *   <li>{@link DirectSer} — small writer, inlined, so {@code writeName} + {@code writeString} + the
 *       ASCII copy loop all land in one frame. This is the Quarkus accessor shape.</li>
 *   <li>{@link IndirectSer} — megamorphic call per property, not inlined, so each write is compiled
 *       in its own small frame. This is the shape Jackson's own reflection path gets for free.</li>
 * </ul>
 *
 * <p>Run it four ways to attribute the gap to a specific callee:
 * <pre>
 * P=tools/jackson/core/json/UTF8JsonGenerator
 * java -jar target/benchmarks.jar WritePathBench
 * java -jar target/benchmarks.jar WritePathBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeString"
 * java -jar target/benchmarks.jar WritePathBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeName"
 * java -jar target/benchmarks.jar WritePathBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeString -XX:CompileCommand=dontinline,$P.writeName"
 * </pre>
 *
 * <p>If {@code direct} is slower than {@code indirect} and a {@code dontinline} on a given callee
 * closes the gap, that callee is a path that suffers. Measured in the Quarkus app, per request and
 * against reflection: {@code writeName} 1.67x, {@code appendQuotedUTF8} 1.60x, {@code writeString}
 * 1.31x, {@code _writeStringSegment} 1.28x — this benchmark is meant to reproduce that ordering
 * without the ~15% machine noise a full HTTP benchmark carries.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class WritePathBench {

    /** 20 elements, as the Quarkus endpoint returns. */
    @Param("20")
    public int size;

    /** Value length: short values make the per-property overhead dominate, long ones the copy loop. */
    @Param({ "10", "40" })
    public int len;

    private ObjectWriter directWriter;
    private ObjectWriter indirectWriter;
    private List<Bean5> values;
    private Sink out;

    @Setup
    public void setup() {
        SimpleModule direct = new SimpleModule("direct");
        direct.addSerializer(Bean5.class, new DirectSer());
        directWriter = JsonMapper.builder().addModule(direct).build().writer()
                .forType(new TypeReference<List<Bean5>>() {
                });

        SimpleModule indirect = new SimpleModule("indirect");
        indirect.addSerializer(Bean5.class, new IndirectSer());
        indirectWriter = JsonMapper.builder().addModule(indirect).build().writer()
                .forType(new TypeReference<List<Bean5>>() {
                });

        String pad = "x".repeat(Math.max(0, len - 1));
        values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Bean5 b = new Bean5();
            b.p0 = "a" + pad;
            b.p1 = "b" + pad;
            b.p2 = "c" + pad;
            b.p3 = "d" + pad;
            b.p4 = "e" + pad;
            values.add(b);
        }
        out = new Sink(256 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    /** Accessor shape: per-property writer inlined, everything piles into one frame. */
    @Benchmark
    public long direct() {
        out.reset();
        directWriter.writeValue(out, values);
        return out.count();
    }

    /** Reflection shape: megamorphic per-property call, each write compiled in its own frame. */
    @Benchmark
    public long indirect() {
        out.reset();
        indirectWriter.writeValue(out, values);
        return out.count();
    }
}
