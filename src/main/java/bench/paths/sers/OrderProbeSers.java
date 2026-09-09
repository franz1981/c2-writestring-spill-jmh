package bench.paths.sers;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.SerializationContext;

import org.openjdk.jmh.annotations.CompilerControl;

import bench.paths.beans.GenPerson;

/**
 * Variants of {@link GenPersonSer} that keep everything constant except what sits between - or
 * before - the two {@code writeString} calls. Used to find what the register allocator is reacting
 * to when it leaves the second copy's loop counter in memory.
 */
public final class OrderProbeSers {

    private static final SerializedString AGE = new SerializedString("age");
    private static final SerializedString FIRST_NAME = new SerializedString("firstName");
    private static final SerializedString HEIGHT = new SerializedString("height");
    private static final SerializedString LAST_NAME = new SerializedString("lastName");

    private static boolean shouldSerialize(Object value) {
        return value != null;
    }

    private static void writeFieldName(JsonGenerator g, String name, SerializableString encoded) {
        g.writeName(encoded);
    }

    private OrderProbeSers() {
    }

    /** A: the generated order - double between the two strings. This is the baseline. */
    public static final class A extends GeneratedSer<GenPerson> {
        public A() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            int age = v.getAge();
            if (shouldSerialize(Integer.valueOf(age))) { writeFieldName(g, "age", AGE); g.writeNumber(age); }
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            double h = v.getHeight();
            if (shouldSerialize(Double.valueOf(h))) { writeFieldName(g, "height", HEIGHT); g.writeNumber(h); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
        }
    }

    /** B: the two strings only - nothing between them, no numbers at all. */
    public static final class B extends GeneratedSer<GenPerson> {
        public B() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
        }
    }

    /** C: both strings first, both numbers after - same four properties, strings adjacent. */
    public static final class C extends GeneratedSer<GenPerson> {
        public C() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
            int age = v.getAge();
            if (shouldSerialize(Integer.valueOf(age))) { writeFieldName(g, "age", AGE); g.writeNumber(age); }
            double h = v.getHeight();
            if (shouldSerialize(Double.valueOf(h))) { writeFieldName(g, "height", HEIGHT); g.writeNumber(h); }
        }
    }

    /** D: both numbers first, strings adjacent at the end. */
    public static final class D extends GeneratedSer<GenPerson> {
        public D() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            int age = v.getAge();
            if (shouldSerialize(Integer.valueOf(age))) { writeFieldName(g, "age", AGE); g.writeNumber(age); }
            double h = v.getHeight();
            if (shouldSerialize(Double.valueOf(h))) { writeFieldName(g, "height", HEIGHT); g.writeNumber(h); }
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
        }
    }

    /** E: only the int between the strings - is it the double specifically, or any property? */
    public static final class E extends GeneratedSer<GenPerson> {
        public E() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            int age = v.getAge();
            if (shouldSerialize(Integer.valueOf(age))) { writeFieldName(g, "age", AGE); g.writeNumber(age); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
        }
    }

    /** F: only the double between the strings, no int. */
    public static final class F extends GeneratedSer<GenPerson> {
        public F() { super(GenPerson.class); }
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        @Override
        public void serializeContent(GenPerson v, JsonGenerator g, SerializationContext c) {
            String f = v.getFirstName();
            if (shouldSerialize(f)) { writeFieldName(g, "firstName", FIRST_NAME); g.writeString(f); }
            double h = v.getHeight();
            if (shouldSerialize(Double.valueOf(h))) { writeFieldName(g, "height", HEIGHT); g.writeNumber(h); }
            String l = v.getLastName();
            if (shouldSerialize(l)) { writeFieldName(g, "lastName", LAST_NAME); g.writeString(l); }
        }
    }
}
