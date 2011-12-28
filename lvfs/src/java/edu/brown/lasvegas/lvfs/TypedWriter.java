package edu.brown.lasvegas.lvfs;

import java.io.Closeable;
import java.io.IOException;

/**
 * A writer that provides methods to write typed values.
 * <p>This object allows per-tuple and tuple-aware operations unlike {@link RawValueWriter}.
 * However, also unlike {@link RawValueWriter}, this object does not provide
 * raw operations such as writeBytes() and writeLong() which will break how tuple is
 * managed in this object.</p>
 * 
 * <p>Note: the caller should make sure they call {@link #writeFileFooter()},
 * {@link #flush()}, and then {@link #close()} to complete the write.</p>
 * @param <T> Value type
 * @param <AT> Array type 
 */
public interface TypedWriter<T, AT> extends Closeable {
    /**
     * Writes a single value. Avoid using this,
     * and instead use {@link #writeValues(Object, int, int)} whenever possible.
     * @param value the value to write out
     * @throws IOException
     */
    void writeValue (T value) throws IOException;

    /**
     * Writes arbitrary number of values at once.
     * @param values the values to write out
     * @param off offset of the values
     * @param len number of values to write
     * @throws IOException
     */
    void writeValues (AT values, int off, int len) throws IOException;

    /**
     * Writes a footer for the file.
     * This method should be called only once at the end of file creation.
     * Failing to call this method results in a corrupted file without footer
     * although only some files actually have a file-footer.
     * @return CRC32 value of the written file.
     * @throws IOException
     */
    int writeFileFooter () throws IOException;

    /**
     * this version only flushes the underlying stream, does not call sync.
     */
    void flush () throws IOException;
    /**
     * @param sync whether to call getFD().sync(). This makes sure the written
     * data is durable, but this might be costly. As Hadoop application does not 
     * need 100% ACID, asynchronous write by OS might be enough. 
     */
    void flush (boolean sync) throws IOException;
    /** Close the file. */
    void close () throws IOException;
}
