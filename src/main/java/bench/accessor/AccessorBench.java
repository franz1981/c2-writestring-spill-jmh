package bench.accessor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
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
import bench.paths.beans.Address;
import bench.paths.beans.Bool5;
import bench.paths.beans.Car;
import bench.paths.beans.ExtPerson;
import bench.paths.beans.Int5;
import bench.paths.beans.Str1;
import bench.paths.beans.Str5;

/**
 * The real A/B, with the whole accessor stack in place:
 * <ul>
 *   <li><b>accessor</b> — {@link AccessorModifier} swaps in {@link AccessorWriters}, which read via a
 *       {@link PropertyAccessor} and write straight to the generator. Jackson's own
 *       {@code UnrolledBeanSerializer} drives them, so this is the shape the Quarkus app runs.</li>
 *   <li><b>reflection</b> — stock Jackson 3: {@code BeanPropertyWriter} reading through a
 *       {@code MethodHandle} obtained from {@code UnreflectHandleSupplier}, and the value written by
 *       a {@code ValueSerializer}.</li>
 * </ul>
 *
 * <p>Same beans, same output, same Jackson. The only difference is how the property is reached and
 * whether the per-property writer is small enough for C2 to inline — which is exactly the question.
 *
 * <p>One bean per call path: {@code str1}/{@code str5} exercise writeString, {@code int5} writeNumber,
 * and {@code bool5} isolates writeName because writeBoolean emits a constant byte sequence.
 *
 * <pre>
 * P=tools/jackson/core/json/UTF8JsonGenerator
 * java -jar target/benchmarks.jar AccessorBench
 * java -jar target/benchmarks.jar AccessorBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeString"
 * java -jar target/benchmarks.jar AccessorBench -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=dontinline,$P.writeName"
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class AccessorBench {

    @Param("20")
    public int size;

    @Param({ "10", "40" })
    public int len;

    private ObjectWriter str1Acc, str1Refl, str5Acc, str5Refl;
    private ObjectWriter int5Acc, int5Refl, bool5Acc, bool5Refl, extAcc, extRefl, extSwitch;
    private List<Str1> str1s;
    private List<Str5> str5s;
    private List<Int5> int5s;
    private List<Bool5> bool5s;
    private List<ExtPerson> exts;
    private Sink out;

    // ---- the per-request path Quarkus actually runs -------------------------------------------
    // BasicServerJacksonMessageBodyWriter.getWriter(genericType, value):
    //   JacksonMapperUtil.getGenericRootType -> defaultWriter.getTypeFactory().constructType(genericType)
    //   rootType.isTypeOrSuperTypeOf(value.getClass())
    //   genericWriters.get(rootType)            (ConcurrentHashMap, populated on first use)
    //   writer.writeValue(stream, value)
    // The type construction and the map lookup happen on EVERY request; hoisting them into @Setup
    // (as this benchmark did originally) hides the serializer-cache and TypeFactory work that shows
    // up in the app's profile - PrivateMaxEntriesMap, LinkedDeque, TypeFactory, TypeBindings.
    private Type extGenericType;
    private ObjectWriter extDefaultAcc;
    private ObjectWriter extDefaultRefl;
    private final ConcurrentHashMap<JavaType, ObjectWriter> accWriters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JavaType, ObjectWriter> reflWriters = new ConcurrentHashMap<>();

    private static List<ExtPerson> genericTypeCarrier() {
        return null;
    }

    private ObjectWriter quarkusWriter(ObjectWriter defaultWriter,
            ConcurrentHashMap<JavaType, ObjectWriter> cache, Object value) {
        JavaType rootType = defaultWriter.getTypeFactory().constructType(extGenericType);
        if (rootType != null && rootType.isTypeOrSuperTypeOf(value.getClass())) {
            ObjectWriter w = cache.get(rootType);
            if (w == null) {
                w = cache.computeIfAbsent(rootType, defaultWriter::forType);
            }
            return w;
        }
        return defaultWriter;
    }

    private static ObjectWriter reflective(TypeReference<?> listType) {
        return JsonMapper.builder().build().writer().forType(listType);
    }

    private static ObjectWriter withAccessor(Class<?> bean, PropertyAccessor accessor, int[] kinds,
            TypeReference<?> listType) {
        Map<Class<?>, PropertyAccessor> a = new HashMap<>();
        a.put(bean, accessor);
        Map<Class<?>, int[]> k = new HashMap<>();
        k.put(bean, kinds);
        SimpleModule m = new SimpleModule("accessor-" + bean.getSimpleName());
        m.setSerializerModifier(new AccessorModifier(a, k));
        return JsonMapper.builder().addModule(m).build().writer().forType(listType);
    }

    /** The end-to-end case needs accessors for the bean and both nested beans. */
    private static ObjectWriter extPersonAccessor(TypeReference<?> listType) {
        return extPersonAccessor(listType, false);
    }

    private static ObjectWriter extPersonAccessor(TypeReference<?> listType, boolean singleWriterClass) {
        Map<Class<?>, PropertyAccessor> a = new HashMap<>();
        a.put(ExtPerson.class, new Accessors.ExtPersonAccessor());
        a.put(Address.class, new Accessors.AddressAccessor());
        a.put(Car.class, new Accessors.CarAccessor());
        Map<Class<?>, int[]> k = new HashMap<>();
        k.put(ExtPerson.class, new int[] { AccessorWriters.KIND_STRING, AccessorWriters.KIND_STRING,
                AccessorWriters.KIND_INT, AccessorWriters.KIND_OBJECT, AccessorWriters.KIND_OBJECT });
        k.put(Address.class, all(AccessorWriters.KIND_STRING, 2));
        k.put(Car.class, all(AccessorWriters.KIND_STRING, 2));
        SimpleModule m = new SimpleModule("accessor-ExtPerson-" + singleWriterClass);
        m.setSerializerModifier(new AccessorModifier(a, k, singleWriterClass));
        return JsonMapper.builder().addModule(m).build().writer().forType(listType);
    }

    private static int[] all(int kind, int n) {
        int[] k = new int[n];
        java.util.Arrays.fill(k, kind);
        return k;
    }

    @Setup
    public void setup() {
        TypeReference<List<Str1>> t1 = new TypeReference<>() {};
        TypeReference<List<Str5>> t5 = new TypeReference<>() {};
        TypeReference<List<Int5>> ti = new TypeReference<>() {};
        TypeReference<List<Bool5>> tb = new TypeReference<>() {};

        str1Acc = withAccessor(Str1.class, new Accessors.Str1Accessor(),
                all(AccessorWriters.KIND_STRING, 1), t1);
        str1Refl = reflective(t1);
        str5Acc = withAccessor(Str5.class, new Accessors.Str5Accessor(),
                all(AccessorWriters.KIND_STRING, 5), t5);
        str5Refl = reflective(t5);
        int5Acc = withAccessor(Int5.class, new Accessors.Int5Accessor(),
                all(AccessorWriters.KIND_INT, 5), ti);
        int5Refl = reflective(ti);
        bool5Acc = withAccessor(Bool5.class, new Accessors.Bool5Accessor(),
                all(AccessorWriters.KIND_BOOLEAN, 5), tb);
        bool5Refl = reflective(tb);

        TypeReference<List<ExtPerson>> te = new TypeReference<>() {};
        extAcc = extPersonAccessor(te);
        extRefl = reflective(te);
        extSwitch = extPersonAccessor(te, true);

        try {
            extGenericType = AccessorBench.class.getDeclaredMethod("genericTypeCarrier")
                    .getGenericReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        // default writers WITHOUT forType, exactly as Quarkus's defaultWriter is built
        Map<Class<?>, PropertyAccessor> qAcc = new HashMap<>();
        qAcc.put(ExtPerson.class, new Accessors.ExtPersonAccessor());
        qAcc.put(Address.class, new Accessors.AddressAccessor());
        qAcc.put(Car.class, new Accessors.CarAccessor());
        Map<Class<?>, int[]> qKinds = new HashMap<>();
        qKinds.put(ExtPerson.class, new int[] { AccessorWriters.KIND_STRING, AccessorWriters.KIND_STRING,
                AccessorWriters.KIND_INT, AccessorWriters.KIND_OBJECT, AccessorWriters.KIND_OBJECT });
        qKinds.put(Address.class, all(AccessorWriters.KIND_STRING, 2));
        qKinds.put(Car.class, all(AccessorWriters.KIND_STRING, 2));
        SimpleModule qm = new SimpleModule("accessor-quarkus-path");
        qm.setSerializerModifier(new AccessorModifier(qAcc, qKinds));
        extDefaultAcc = JsonMapper.builder().addModule(qm).build().writer();
        extDefaultRefl = JsonMapper.builder().build().writer();

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
        // the app returns Collections.nCopies(20, ONE instance): the same object 20 times, with the
        // nested Address and Car shared across all elements. Distinct instances change locality and
        // escape analysis, so mirror it exactly.
        exts = java.util.Collections.nCopies(size,
                new ExtPerson("John" + pad, "Doe" + pad, 30,
                        new Address("Milano" + pad, "Via Roma" + pad),
                        new Car("Fiat" + pad, "500" + pad)));
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

    @Benchmark public long str1_accessor()   { return write(str1Acc, str1s); }
    @Benchmark public long str1_reflection() { return write(str1Refl, str1s); }

    @Benchmark public long str5_accessor()   { return write(str5Acc, str5s); }
    @Benchmark public long str5_reflection() { return write(str5Refl, str5s); }

    @Benchmark public long int5_accessor()   { return write(int5Acc, int5s); }
    @Benchmark public long int5_reflection() { return write(int5Refl, int5s); }

    @Benchmark public long bool5_accessor()   { return write(bool5Acc, bool5s); }
    @Benchmark public long bool5_reflection() { return write(bool5Refl, bool5s); }

    /** The end-to-end bean: nested Address and Car, whose serializers are inlined into this frame. */
    @Benchmark public long extPerson_accessor()   { return write(extAcc, exts); }
    @Benchmark public long extPerson_reflection() { return write(extRefl, exts); }

    /** One writer class for all kinds: monomorphic call site, fatter inlined body. */
    @Benchmark public long extPerson_switchWriter() { return write(extSwitch, exts); }

    /** Same, but through the per-request path Quarkus runs: constructType + writer-cache lookup. */
    @Benchmark
    public long extPerson_accessor_quarkusPath() {
        out.reset();
        quarkusWriter(extDefaultAcc, accWriters, exts).writeValue(out, exts);
        return out.count();
    }

    @Benchmark
    public long extPerson_reflection_quarkusPath() {
        out.reset();
        quarkusWriter(extDefaultRefl, reflWriters, exts).writeValue(out, exts);
        return out.count();
    }
}
