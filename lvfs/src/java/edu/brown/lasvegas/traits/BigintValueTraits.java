package edu.brown.lasvegas.traits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import edu.brown.lasvegas.lvfs.RawValueReader;
import edu.brown.lasvegas.lvfs.RawValueWriter;
import edu.brown.lasvegas.lvfs.TypedRLEWriter;
import edu.brown.lasvegas.lvfs.ValueRun;
import edu.brown.lasvegas.util.KeyValueArrays;

/** Traits for BIGINT (java Long/long[]). DATE/TIME/TIMESTAMP also fall into this (internally). */
public final class BigintValueTraits implements FixLenValueTraits<Long, long[]> {
    @Override
    public Long readValue(RawValueReader reader) throws IOException {
        return reader.readLong();
    }
    @Override
    public int readValues(RawValueReader reader, long[] buffer, int off, int len) throws IOException {
        return reader.readLongs(buffer, off, len);
    }
    @Override
    public void writeValue(RawValueWriter writer, Long value) throws IOException {
        writer.writeLong(value);
    }
    @Override
    public void writeValues(RawValueWriter writer, long[] values, int off, int len) throws IOException {
        writer.writeLongs(values, off, len);
    }
    @Override
    public short getBitsPerValue() {
        return 64;
    }
    @Override
    public void writeRunLengthes(TypedRLEWriter<Long, long[]> writer, long[] values, int off, int len) throws IOException {
        ValueRun<Long> cur = writer.getCurrentRun();
        long curValue = cur.value;
        for (int i = off; i < off + len; ++i) {
            if (values[i] == curValue) {
                ++cur.runLength;
            } else {
                cur = writer.startNewRun(values[i], 1);
                curValue = values[i];
            }
        }
    }
    @Override
    public long[] createArray(int size) {
        return new long[size];
    }
    @Override
    public int length(long[] array) {
        return array.length;
    }
    @Override
    public long[] toArray(Collection<Long> values) {
        final int length = values.size();
        long[] array = createArray(length);
        Iterator<Long> it = values.iterator();
        for (int i = 0; i < length; ++i) {
            array[i] = it.next();
        }
        return array;
    }
    @Override
    public int binarySearch(long[] array, Long value) {
        return Arrays.binarySearch(array, value);
    }
    @Override
    public void sort(long[] keys) {
        sort (keys, 0, keys.length);
    }
    @Override
    public void sort(long[] keys, int fromIndex, int toIndex) {
        Arrays.sort(keys, fromIndex, toIndex);
    }
    @Override
    public void sortKeyValue(long[] keys, int[] values) {
        sortKeyValue (keys, values, 0, keys.length);
    }
    @Override
    public void sortKeyValue(long[] keys, int[] values, int fromIndex, int toIndex) {
        KeyValueArrays.sort(keys, values, fromIndex, toIndex);
    }
    @Override
    public long[] reorder(long[] src, int[] srcPos) {
        final int len = srcPos.length;
        assert (src.length == len);
        long[] dest = createArray(len);
        for (int i = 0; i < len; ++i) {
            dest[i] = src[srcPos[i]];
        }
        return dest;
    }
    @Override
    public int countDistinct(long[] array) {
        final int len = array.length;
        if (len == 0) return 0;
        long prev = array[0];
        int distinctValues = 1;
        for (int i = 1; i < len; ++i) {
            if (array[i] != prev) {
                ++distinctValues;
                prev = array[i];
            }
        }
        return distinctValues;
    }
    @Override
    public void fillArray(Long value, long[] array, int off, int len) {
        Arrays.fill(array, off, off + len, value);
    }
    @Override
    public Long get(long[] array, int index) {
        return array[index];
    }
    @Override
    public void set(long[] array, int index, Long value) {
        array[index] = value;
    }
    @Override
    public long[] deserializeArray(ByteBuffer buffer) {
        int length = buffer.getInt();
        assert (length >= -1);
        if (length == -1) return null;
        long[] array = createArray(length);
        buffer.asLongBuffer().get(array); // remember this doesn't advance the original byte buffer's position
        buffer.position(buffer.position() + array.length * (getBitsPerValue() / 8)); // so, advance it here
        return array;
    }
    @Override
    public int serializeArray(long[] array, ByteBuffer buffer) {
        if (array == null) {
            buffer.putInt(-1);
            return 4;
        }
        buffer.putInt(array.length);
        buffer.asLongBuffer().put(array); // remember this doesn't advance the original byte buffer's position
        buffer.position(buffer.position() + array.length * (getBitsPerValue() / 8)); // so, advance it here
        return 4 + array.length * (getBitsPerValue() / 8);
    }
    @Override
    public int getSerializedByteSize(long[] array) {
        if (array == null) return 4;
        return 4 + array.length * (getBitsPerValue() / 8);
    }
}