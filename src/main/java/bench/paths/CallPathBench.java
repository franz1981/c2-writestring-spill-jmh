package bench.paths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
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
import bench.paths.beans.Bool5;
import bench.paths.beans.Int5;
import bench.paths.beans.Str1;
import bench.paths.beans.Str5;
import bench.paths.sers.Bool5Ser;
import bench.paths.sers.Int5Ser;
import bench.paths.sers.Str1Ser;
import bench.paths.sers.Str5Ser;

/**
 * Isolates each generator call path, in the same style as {@code bench.flat.SingleBench}: a
 * hand-written serializer per bean, in the shape a code generator emits, run once as is and once
 * with a {@code dontinline} on the callee under test.
 *
 * <p>Each bean isolates a different write path:
 * <table>
 *   <tr><td>{@code str1}</td><td>one String — a single writeName + writeString</td></tr>
 *   <tr><td>{@code str5}</td><td>five Strings — the ExtendedPerson shape</td></tr>
 *   <tr><td>{@code int5}</td><td>five ints — writeNumber; <b>no writeString anywhere</b></td></tr>
 *   <tr><td>{@code bool5}</td><td>five booleans — writeBoolean emits a constant byte sequence, so
 *       <b>writeName is the only real work</b>. This is the one that isolates the name path.</td></tr>
 * </table>
 *
 * <p>Run the matrix; a callee is implicated when removing it from the frame moves its bean:
 * <pre>
 * P=tools/jackson/core/json/UTF8JsonGenerator
 * java -jar target/benchmarks.jar CallPathBench
 * java -jar target/benchmarks.jar CallPathBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeString"
 * java -jar target/benchmarks.jar CallPathBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeName"
 * </pre>
 *
 * <p>Measured in the Quarkus app, per request and relative to reflection: {@code writeName} 1.67x,
 * {@code appendQuotedUTF8} 1.60x, {@code writeString} 1.31x, {@code _writeStringSegment} 1.28x. In the
 * first JMH run of the string path, {@code dontinline writeString} moved it 3506 -> 2347 ns/op while
 * {@code dontinline writeName} made it worse (3757), so the app's cost attribution and the JMH
 * causal test disagree about {@code writeName} — which is what {@code bool5} is here to settle.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class CallPathBench {

    /** Elements per document; the Quarkus endpoint returns 20. */
    @Param("20")
    public int size;

    /** String value length: short makes per-property overhead dominate, long the copy loop. */
    @Param({ "10", "40" })
    public int len;

    private ObjectWriter str1W, str5W, int5W, bool5W;
    private List<Str1> str1s;
    private List<Str5> str5s;
    private List<Int5> int5s;
    private List<Bool5> bool5s;
    private Sink out;

    private static <T> ObjectWriter writerFor(Class<T> type, ValueSerializer<T> ser, TypeReference<?> listType) {
        SimpleModule m = new SimpleModule(type.getSimpleName());
        m.addSerializer(type, ser);
        return JsonMapper.builder().addModule(m).build().writer().forType(listType);
    }

    @Setup
    public void setup() {
        str1W = writerFor(Str1.class, new Str1Ser(), new TypeReference<List<Str1>>() {});
        str5W = writerFor(Str5.class, new Str5Ser(), new TypeReference<List<Str5>>() {});
        int5W = writerFor(Int5.class, new Int5Ser(), new TypeReference<List<Int5>>() {});
        bool5W = writerFor(Bool5.class, new Bool5Ser(), new TypeReference<List<Bool5>>() {});

        String pad = "x".repeat(Math.max(0, len - 1));
        str1s = new ArrayList<>(size);
        str5s = new ArrayList<>(size);
        int5s = new ArrayList<>(size);
        bool5s = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Str1 a = new Str1();
            a.p0 = "a" + pad;
            str1s.add(a);
            Str5 b = new Str5();
            b.p0 = "a" + pad; b.p1 = "b" + pad; b.p2 = "c" + pad; b.p3 = "d" + pad; b.p4 = "e" + pad;
            str5s.add(b);
            int5s.add(new Int5());
            bool5s.add(new Bool5());
        }
        out = new Sink(1024 * 1024);
    }

    @TearDown
    public void tearDown() throws IOException {
        out.close();
    }

    private long write(ObjectWriter w, Object v) {
        out.reset();
        w.writeValue(out, v);
        return out.count();
    }

    /** One writeName + writeString. */
    @Benchmark public long str1() { return write(str1W, str1s); }

    /** Five writeName + writeString - the shape the Quarkus endpoint serializes. */
    @Benchmark public long str5() { return write(str5W, str5s); }

    /** writeNumber path; no writeString in the frame at all. */
    @Benchmark public long int5() { return write(int5W, int5s); }

    /** writeName isolated: writeBoolean writes a constant, so nothing else does real work. */
    @Benchmark public long bool5() { return write(bool5W, bool5s); }
}
