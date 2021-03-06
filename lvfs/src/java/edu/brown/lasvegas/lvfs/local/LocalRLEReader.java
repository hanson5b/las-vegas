package edu.brown.lasvegas.lvfs.local;

import java.io.IOException;

import org.apache.log4j.Logger;

import edu.brown.lasvegas.lvfs.PositionIndex.Pos;
import edu.brown.lasvegas.lvfs.TypedRLEReader;
import edu.brown.lasvegas.lvfs.ValueRun;
import edu.brown.lasvegas.lvfs.VirtualFile;
import edu.brown.lasvegas.traits.BigintValueTraits;
import edu.brown.lasvegas.traits.DoubleValueTraits;
import edu.brown.lasvegas.traits.FloatValueTraits;
import edu.brown.lasvegas.traits.IntegerValueTraits;
import edu.brown.lasvegas.traits.SmallintValueTraits;
import edu.brown.lasvegas.traits.TinyintValueTraits;
import edu.brown.lasvegas.traits.ValueTraits;
import edu.brown.lasvegas.traits.VarbinValueTraits;
import edu.brown.lasvegas.traits.VarcharValueTraits;
import edu.brown.lasvegas.util.ByteArray;

/**
 * File reader for RLE compressed column.
 * For the description of file format, see the comments of {@link LocalRLEWriter}.
 */
public final class LocalRLEReader<T extends Comparable<T>, AT> extends LocalTypedReaderBase<T, AT> implements TypedRLEReader<T, AT> {
    private static Logger LOG = Logger.getLogger(LocalRLEWriter.class);
    private final ValueTraits<T, AT> traits;

    /** Constructs an instance for 1-byte fixed length integer values. */
    public static LocalRLEReader<Byte, byte[]> getInstanceTinyint(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Byte, byte[]>(rawFile, new TinyintValueTraits());
    }
    /** Constructs an instance for 2-byte fixed length integer values. */
    public static LocalRLEReader<Short, short[]> getInstanceSmallint(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Short, short[]>(rawFile, new SmallintValueTraits());
    }
    /** Constructs an instance for 4-byte fixed length integer values. */
    public static LocalRLEReader<Integer, int[]> getInstanceInteger(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Integer, int[]>(rawFile, new IntegerValueTraits());
    }
    /** Constructs an instance for 8-byte fixed length integer values. */
    public static LocalRLEReader<Long, long[]> getInstanceBigint(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Long, long[]>(rawFile, new BigintValueTraits());
    }
    /** Constructs an instance for 4-byte fixed length float values. */
    public static LocalRLEReader<Float, float[]> getInstanceFloat(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Float, float[]>(rawFile, new FloatValueTraits());
    }
    /** Constructs an instance for 8-byte fixed length float values. */
    public static LocalRLEReader<Double, double[]> getInstanceDouble(VirtualFile rawFile) throws IOException {
        return new LocalRLEReader<Double, double[]>(rawFile, new DoubleValueTraits());
    }
    /** Constructs an instance of varchar column. */
    public static LocalRLEReader<String, String[]> getInstanceVarchar(VirtualFile dataFile) throws IOException {
        return new LocalRLEReader<String, String[]>(dataFile, new VarcharValueTraits());
    }
    /** Constructs an instance of varbinary column. */
    public static LocalRLEReader<ByteArray, ByteArray[]> getInstanceVarbin(VirtualFile dataFile) throws IOException {
        return new LocalRLEReader<ByteArray, ByteArray[]>(dataFile, new VarbinValueTraits());
    }

    public LocalRLEReader(VirtualFile dataFile, ValueTraits<T, AT> traits) throws IOException {
        this (dataFile, null, traits);
    }
    public LocalRLEReader(VirtualFile dataFile, VirtualFile posFile, ValueTraits<T, AT> traits) throws IOException {
        this (dataFile, posFile, traits, 1 << 16);
    }
    /**
     * Constructs a RLE-compression reader. The optional position file speeds up seeking
     * @param dataFile required. the main data file
     * @param posFile optional. position file to speed up locating tuple. (without it, seeking might be slow, but not as much as non-compressed file)
     */
    public LocalRLEReader(VirtualFile dataFile, VirtualFile posFile, ValueTraits<T, AT> traits, int streamBufferSize) throws IOException {
        super (dataFile, traits, streamBufferSize);
        this.traits = traits;
        if (posFile != null) {
            loadPositionFile(posFile);
        }
    }
    private int curTuple = 0;
    private ValueRun<T> curRun = new ValueRun<T>(0, 0, null);
    
    @Override
    public final ValueRun<T> getCurrentRun() throws IOException {
        return curRun;
    }
    @Override
    public final ValueRun<T> getNextRun() throws IOException {
        if (!getRawValueReader().hasMore()) {
            // reached EOF
            curTuple = curRun.startTuple + curRun.runLength;
            curRun.startTuple = curTuple;
            curRun.value = null;
            curRun.runLength = 0;
            return null;
        }
        int endOfCurrentRun = curRun.startTuple + curRun.runLength;
        int newLength = getRawValueReader().readInt();
        T newValue = traits.readValue(getRawValueReader());
        curRun.startTuple = endOfCurrentRun;
        curRun.runLength = newLength;
        curRun.value = newValue;
        curTuple = endOfCurrentRun;
        return curRun;
    }
    
    @Override
    public final T readValue() throws IOException {
        assert (curTuple >= curRun.startTuple);
        if (curTuple >= curRun.startTuple + curRun.runLength) {
            ValueRun<T> next = getNextRun();
            if (next == null) {
                throw new IOException ("EOF");
            }
        }
        ++curTuple;
        return curRun.value;
    }
    @Override
    public final int readValues(AT buffer, int off, int len) throws IOException {
        assert (curTuple >= curRun.startTuple);
        if (len == 0) {
            return 0;
        }
        int totalRead = 0;
        while (curTuple + len > curRun.startTuple + curRun.runLength) {
            int remainingRun = curRun.startTuple + curRun.runLength - curTuple;
            assert (remainingRun >= 0);
            if (remainingRun > 0) {
                traits.fillArray(curRun.value, buffer, off, remainingRun);
                off += remainingRun;
                len -= remainingRun;
                totalRead += remainingRun;
            }
            ValueRun<T> next = getNextRun();
            if (next == null) {
                if (totalRead == 0) {
                    return -1;
                } else {
                    return totalRead;
                }
            }
            assert (curTuple == curRun.startTuple); // increased during getNextRun()
        }
        traits.fillArray(curRun.value, buffer, off, len);
        totalRead += len;
        curTuple += len;
        assert (curTuple >= curRun.startTuple);
        assert (curTuple <= curRun.startTuple + curRun.runLength);
        if (totalRead == 0) {
            return -1;
        } else {
            return totalRead;
        }
    }
    
    @Override
    public final void skipValue() throws IOException {
        assert (curTuple >= curRun.startTuple);
        if (curTuple >= curRun.startTuple + curRun.runLength) {
            ValueRun<T> next = getNextRun();
            if (next == null) {
                throw new IOException ("EOF");
            }
        }
        ++curTuple;
    }
    @Override
    public final void skipValues(int skip) throws IOException {
        assert (curTuple >= curRun.startTuple);
        while (curTuple + skip > curRun.startTuple + curRun.runLength) {
            int remainingRun = (curRun.startTuple + curRun.runLength) - curTuple;
            assert (remainingRun >= 0);
            ValueRun<T> next = getNextRun();
            if (next == null) {
                throw new IOException ("EOF");
            }
            skip -= remainingRun;
        }
        assert (curTuple + skip <= curRun.startTuple + curRun.runLength);
        curTuple += skip;
    }
    @Override
    public final void seekToTupleAbsolute(int tuple) throws IOException {
        if (posIndex == null) {
            if (tuple != 0) {
                LOG.warn("seeking to " + tuple + "th tuple without position file. this might be slow!");
            }
            if (tuple < curTuple) {
                // we have to first seek to the beginning
                getRawReader().seekToByteAbsolute(0);
                curTuple = 0;
                curRun.startTuple = 0;
                curRun.runLength = 0;
                curRun.value = null;
            }
            skipValues(tuple);
        } else {
            // seek using the position file
            Pos pos = posIndex.searchPosition(tuple);
            getRawReader().seekToByteAbsolute(pos.bytePosition);
            curTuple = pos.tuple;
            assert (curTuple <= tuple);
            // dummy run to do getNextRun()
            curRun.startTuple = curTuple;
            curRun.runLength = 0;
            curRun.value = null;
            ValueRun<T> next =  getNextRun();
            assert (next.startTuple == curTuple);
            // remaining is sequential search
            skipValues(tuple - curTuple);
        }
    }
    @Override
    public final int getTotalTuples() {
        // we can answer the number of tuples only when position file is given
        if (posIndex == null) {
            throw new UnsupportedOperationException();
        } else {
            return posIndex.getTotalTuples();
        }
    }
}
