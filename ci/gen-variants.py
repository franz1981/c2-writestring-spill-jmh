#!/usr/bin/env python3
"""Generate UTF8JsonGenerator variants from pristine Jackson 3.1.4 sources.

Arms (all also carry jackson-databind #6183, so the charAt MDO-pollution issue is not a variable):
  base    3.1.4 as shipped:  ch > 0x7F || escCodes[ch] != 0
  const   jackson-core#1681: constant escape test, no table load
  t64k    64K byte table indexed by the char, no bounds check (franz1981's PR comment)
  gc      #1707 core change: getChars() into _charBuffer, scan the char[]
  gct64k  gc + t64k
  gcconst gc + const
"""
import os, sys

CORE = sys.argv[1]      # pristine UTF8JsonGenerator.java
DBIND = sys.argv[2]     # pristine StdDateFormat.java
OUT = sys.argv[3]

READERS = ("cbuf[offset]", "text.charAt(offset)")

def loop(reader):
    return ("""        while (offset < len) {
            int ch = """ + reader + """;
            // note: here we know that (ch > 0x7F) will cover case of escaping non-ASCII too:
            if (ch > 0x7F || escCodes[ch] != 0) {
                break;
            }
            outputBuffer[outputPtr++] = (byte) ch;
            ++offset;
        }""")

STD_ESC = "        final boolean stdEsc = (escCodes == tools.jackson.core.io.CharTypes.get7BitOutputEscapes());\n"

def with_test(reader, test):
    return (STD_ESC + """        while (offset < len) {
            int ch = """ + reader + """;
            if (stdEsc ? (""" + test + """)
                       : (ch > 0x7F || escCodes[ch] != 0)) {
                break;
            }
            outputBuffer[outputPtr++] = (byte) ch;
            ++offset;
        }""")

TABLE_FIELD = '''
    /** Non-zero == this char can be copied out as one byte. 65536 entries so a char index needs no
     *  range check; only ~94 entries are non-zero, the rest stay at Java's default zero. */
    private static final byte[] SAFE_ASCII_64K = new byte[65536];
    static {
        for (int i = 0x20; i < 0x80; i++) { SAFE_ASCII_64K[i] = 1; }
        SAFE_ASCII_64K['"'] = 0;
        SAFE_ASCII_64K['\\\\'] = 0;
    }
'''

GETCHARS_OLD = """        final int len = text.length();
        if (len > _outputMaxContiguous) { // nope: off-line handling
            _writeStringSegments(text, true);
            return this;
        }
        if ((_outputTail + len) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = _quoteChar;
        _writeStringSegment(text, 0, len); // we checked space already above"""

GETCHARS_NEW = """        final int len = text.length();
        // #1707: copy via getChars() and scan the char[] instead of charAt() on the String
        if ((len > _outputMaxContiguous) || (len > _charBufferLength)) {
            _writeStringSegments(text, true);
            return this;
        }
        if ((_outputTail + len) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = _quoteChar;
        text.getChars(0, len, _charBuffer, 0);
        _writeStringSegment(_charBuffer, 0, len);"""

CONST_TEST = """ch < 0x20 || ch > 0x7F || ch == '"' || ch == '\\\\'"""
T64K_TEST  = "SAFE_ASCII_64K[ch] == 0"

def build(name, pristine, test=None, table=False, getchars=False):
    s = pristine
    if test is not None:
        for r in READERS:
            old = loop(r)
            assert old in s, f"{name}: loop not found for {r}"
            s = s.replace(old, with_test(r, test), 1)
    if table:
        i = s.index("public class UTF8JsonGenerator"); j = s.index("{", i) + 1
        s = s[:j] + TABLE_FIELD + s[j:]
    if getchars:
        assert GETCHARS_OLD in s, f"{name}: getChars call site not found"
        s = s.replace(GETCHARS_OLD, GETCHARS_NEW, 1)
    d = os.path.join(OUT, name, "tools/jackson/core/json")
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, "UTF8JsonGenerator.java"), "w").write(s)
    print(f"  {name}: written")

# jackson-databind #6183 - lazy RFC1123 blueprint, shared by every arm
def databind(pristine):
    old = """    protected final static DateFormat DATE_FORMAT_RFC1123;

    /* Let's construct "blueprint" date format instances: cannot be used
     * as is, due to thread-safety issues, but can be used for constructing
     * actual instances more cheaply (avoids re-parsing).
     */
    static {
        // Another important thing: let's force use of default timezone for
        // baseline DataFormat objects
        DATE_FORMAT_RFC1123 = new SimpleDateFormat(DATE_FORMAT_STR_RFC1123, DEFAULT_LOCALE);
        DATE_FORMAT_RFC1123.setTimeZone(DEFAULT_TIMEZONE);
    }"""
    new = """    /** #6183: built lazily, so <clinit> does not construct a SimpleDateFormat. */
    private static final class RFC1123Holder {
        static final DateFormat DATE_FORMAT_RFC1123;
        static {
            DATE_FORMAT_RFC1123 = new SimpleDateFormat(DATE_FORMAT_STR_RFC1123, DEFAULT_LOCALE);
            DATE_FORMAT_RFC1123.setTimeZone(DEFAULT_TIMEZONE);
        }
    }"""
    assert old in pristine, "StdDateFormat: clinit block not found"
    s = pristine.replace(old, new, 1).replace("_cloneFormat(DATE_FORMAT_RFC1123,",
                                              "_cloneFormat(RFC1123Holder.DATE_FORMAT_RFC1123,")
    d = os.path.join(OUT, "shared/tools/jackson/databind/util")
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, "StdDateFormat.java"), "w").write(s)
    print("  shared StdDateFormat (#6183): written")

core = open(CORE).read()
databind(open(DBIND).read())
build("base",   core)
build("const",  core, test=CONST_TEST)
build("t64k",   core, test=T64K_TEST, table=True)
build("gc",     core, getchars=True)
build("gct64k", core, test=T64K_TEST, table=True, getchars=True)
build("gcconst", core, test=CONST_TEST, getchars=True)
