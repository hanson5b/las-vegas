package edu.brown.lasvegas.lvfs.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;
import org.xerial.snappy.Snappy;

import edu.brown.lasvegas.CompressionType;
import edu.brown.lasvegas.lvfs.RawValueReader;
import edu.brown.lasvegas.lvfs.VirtualFile;
import edu.brown.lasvegas.traits.ValueTraits;

/**
 * File reader for a block-compressed file such as Snappy and LZO.
 * <p>For the format of block-coompressed files, see {@link LocalBlockCompressionWriter}</p>
 */
public abstract class LocalBlockCompressionReader<T extends Comparable<T>, AT> extends LocalTypedReaderBase<T, AT> {
    private static Logger LOG = Logger.getLogger(LocalBlockCompressionReader.class);

    /** compression type for the file. */
    private final CompressionType compressionType;
    /** number of compressed blocks in this file. */
    protected final int blockCount;
    /** number of all tuples in this file. */
    protected final int totalTuples;
    /** List of the tuples to start each block. */
    protected final int[] blockStartTuples;
    /** List of the byte position (in compressed form) of each block. */
    protected final int[] blockPositions;
    /** List of the byte length (in compressed form) of each block. */
    protected final int[] blockLengthes;
    /** List of the tuple counts of each block. */
    protected final int[] blockTupleCounts;
    
    /** the index of the block we are currently at. */
    protected int currentBlockIndex;
    /** data of current block without compression. */
    protected byte[] currentBlock;
    /** where in currentBlock we are at in terms of byte position from the beginning of the block. */
    protected int currentBlockCursor = 0;
    /** current tuple number relative to the beginning of the block. */
    protected int currentBlockTuple = 0;
    private final ProxyValueReader proxyReader;
    protected final ProxyValueReader getProxyValueReader() {
        return proxyReader;
    }
    
    public LocalBlockCompressionReader(VirtualFile file, ValueTraits<T, AT> traits, CompressionType compressionType) throws IOException {
        super (file, traits, 0); // as it's block-compressed, no point to buffer. 
        this.compressionType = compressionType;
        proxyReader = new ProxyValueReader();

        // Reads the block position footer at the end of the file.
        // This is done only once when this class is instantiated.
        final int rawFileSize = getRawReader().getRawFileSize();
        assert (rawFileSize >= 8);
        getRawReader().seekToByteAbsolute(rawFileSize - 8);
        blockCount = getRawValueReader().readInt();
        if (blockCount < 0 || rawFileSize < 8 + 4 * 3 * blockCount) {
            throw new IOException ("invalid file footer. corrupted file? blockCount=" + blockCount + ". file="+ this);
        }
        totalTuples = getRawValueReader().readInt();
        if (totalTuples < 0) {
            throw new IOException ("invalid file footer. corrupted file? blockCount=" + blockCount + ", totalTuples=" + totalTuples + ". file="+ this);
        }
        getRawReader().seekToByteAbsolute(rawFileSize - 8 - 4 * 3 * blockCount);
        int[] intBuf = new int[3 * blockCount];
        int intRead = getRawValueReader().readInts(intBuf, 0, intBuf.length);
        assert (intRead == intBuf.length);
        // the 3*n array can be used as it is.. but it's easier to have variables names for them.
        // so, split the triplets to each array. anyway, this happens only once per file.
        blockStartTuples = new int[blockCount];
        blockPositions = new int[blockCount];
        blockLengthes = new int[blockCount];
        blockTupleCounts = new int[blockCount];
        for (int i = 0; i < blockCount; ++i) {
            blockStartTuples[i] = intBuf[3 * i];
            blockPositions[i] = intBuf[3 * i + 1];
            blockLengthes[i] = intBuf[3 * i + 2];
            if (i == 0) {
                if (blockStartTuples[i] != 0 || blockPositions[i] != 0) {
                    throw new IOException ("invalid footer. "
                       + "blockStartTuples["+i+"]=" + blockStartTuples[i]
                       + ",blockPositions["+i+"]="+blockPositions[i]+" corrupted file? file="+ this);
                }
            } else {
                if (blockStartTuples[i] <= blockStartTuples[i - 1] ||
                        blockPositions[i] <= blockPositions[i - 1] ||
                        blockPositions[i] - blockPositions[i - 1] != blockLengthes[i - 1]) {
                    throw new IOException ("invalid footer. corrupted file? file="+ this);
                }
                blockTupleCounts[i - 1] = blockStartTuples[i] - blockStartTuples[i - 1];
            }
        }
        blockTupleCounts[blockCount - 1] = totalTuples - blockStartTuples[blockCount - 1];
        if (blockPositions[blockCount - 1] + blockLengthes[blockCount - 1] + 8 + 4 * 3 * blockCount != rawFileSize) {
            throw new IOException ("invalid footer. corrupted file? file="+ this);
        }
        getRawReader().seekToByteAbsolute(0); // reset to the beginning of the file
        currentBlockIndex = -1; // in no block
    }
    protected abstract int getCurrentBlockFooterByteSize ();
    /**
     * Proxy reader to read from currentBlock.
     */
    protected class ProxyValueReader extends RawValueReader {
        @Override
        public byte readByte() throws IOException {
            // this should never happen. the derived classes make sure
            // we don't go beyond the end of the block.
            if (currentBlockCursor + 1 > currentBlock.length) {
                throw new IOException ("cannot go beyond the end of current block: currentBlockCursor=" + currentBlockCursor
                                + ", requested len=1, currentBlock.length=" + currentBlock.length);
            }
            byte ret = currentBlock[currentBlockCursor];
            ++currentBlockCursor;
            if (LOG.isTraceEnabled()) {
                LOG.trace("read in compressed block 1 byte");
            }
            return ret;
        }
        @Override
        public int readBytes(byte[] buf, int off, int len) throws IOException {
            // this should never happen. the derived classes make sure
            // we don't go beyond the end of the block.
            if (currentBlockCursor + len > currentBlock.length) {
                throw new IOException ("cannot go beyond the end of current block: currentBlockCursor=" + currentBlockCursor
                                + ", requested len=" + len + ", currentBlock.length=" + currentBlock.length);
            }
            System.arraycopy(currentBlock, currentBlockCursor, buf, off, len);
            currentBlockCursor += len;
            if (LOG.isTraceEnabled()) {
                LOG.trace("read in compressed block " + len + " bytes");
            }
            return len;
        }
        @Override
        public void skipBytes(int length) throws IOException {
            // same as above
            if (currentBlockCursor + length  > currentBlock.length) {
                throw new IOException ("cannot skip beyond the end of current block: currentBlockCursor=" + currentBlockCursor
                                + ", requested skip=" + length + ", currentBlock.length=" + currentBlock.length);
            }
            currentBlockCursor += length;
            if (LOG.isTraceEnabled()) {
                LOG.trace("skipped in compressed block " + length + " bytes");
            }
        }
        @Override
        public boolean hasMore() throws IOException {
            // if there is something in this block, return true.
            // even if not, if this is not the last block, we have more to read
            return currentBlockCursor + getCurrentBlockFooterByteSize() < currentBlock.length || currentBlockIndex < blockCount - 1;
        }
    }
    /**
     * override this to read footer of newly loaded block.
     */
    protected abstract void readBlockFooter () throws IOException;
    /**
     * Moves to the specified block, decompresses it and sets the cursor to the beginning of the block.
     */
    protected final void seekToBlock (int block) throws IOException {
        if (block < 0 || block >= blockCount) {
            throw new IOException ("invalid block specified: " + block);
        }
        if (currentBlockIndex != block) {
            getRawReader().seekToByteAbsolute(blockPositions[block]);
            byte[] buf = new byte[blockLengthes[block]];
            int read = getRawValueReader().readBytes(buf, 0, buf.length);
            assert (read == buf.length);
            if (compressionType == CompressionType.SNAPPY) {
                currentBlock = Snappy.uncompress(buf);
            } else {
                // GZipInputStream doesn't provide the uncompressed size.
                // so, we simply use ByteArrayOutputStream, which might be inefficient.
                // but, the point of CompressionType.GZIP_BEST_COMPRESSION is not performance...
                assert (compressionType == CompressionType.GZIP_BEST_COMPRESSION);
                GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(buf), 1 << 16);
                ByteArrayOutputStream result = new ByteArrayOutputStream(1 << 16);
                byte[] gzipBuf = new byte[1 << 16];
                while (true) {
                    int uncompressedRead = gzip.read(gzipBuf);
                    if (uncompressedRead < 0) {
                        break;
                    }
                    result.write(gzipBuf, 0, uncompressedRead);
                }
                currentBlock = result.toByteArray();
                gzip.close();
            }
            currentBlockIndex = block;
            readBlockFooter ();
            if (LOG.isDebugEnabled()) {
                LOG.debug("read and decompressed a block(" + block + "): " + buf.length + "bytes to " + currentBlock.length + "bytes.");
            }
        }
        currentBlockCursor = 0;
        currentBlockTuple = 0;
    }


    /**
     * Returns the block that contains the specified tuple.
     * @param tupleToFind the tuple position to find.
     * @return the block that contains the specified tuple.
     */
    protected final int searchBlock (int tupleToFind) {
        if (blockStartTuples[0] >= tupleToFind) {
            return 0;
        }
        if (blockStartTuples[blockCount - 1] <= tupleToFind) {
            return blockCount - 1;
        }
        
        // then, binary search
        int low = 0; // the entry we know strictly smaller
        int high = blockCount; // the entry we know strictly larger
        int mid = 0;
        while (low <= high) {
            mid = (low + high) >>> 1;
            int midTuple = blockStartTuples[mid];
            if (midTuple < tupleToFind) {
                low = mid + 1;
            } else if (midTuple > tupleToFind) {
                high = mid - 1;
            } else {
                return mid; // exact match
            }
        }
        // not exact match. in this case, return the position we should start searching
        int ret = (low > mid) ? low - 1 : mid - 1;
        assert (ret >= 0);
        assert (ret < blockCount);
        assert (blockStartTuples[ret] < tupleToFind);
        assert (ret == blockCount - 1 || blockStartTuples[ret + 1] > tupleToFind);
        return ret;
    }
    
    @Override
    public int getTotalTuples() {
        return totalTuples;
    }
}
