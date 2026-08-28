package bench;

import java.io.OutputStream;

/** Reusable output target: copies into a pre-sized array, like a real one, and never allocates. */
public final class Sink extends OutputStream {

    private final byte[] buffer;
    private int position;

    public Sink(int capacity) {
        this.buffer = new byte[capacity];
    }

    @Override
    public void write(int b) {
        buffer[position++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        System.arraycopy(b, off, buffer, position, len);
        position += len;
    }

    public long count() {
        return position;
    }

    public void reset() {
        position = 0;
    }
}
