package edu.brown.lasvegas.lvfs.local;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.xerial.snappy.Snappy;

import edu.brown.lasvegas.CompressionType;
import edu.brown.lasvegas.lvfs.RawValueWriter;

/**
 * File writer for a block-compressed file such as Snappy and LZO.
 * <p>Because of the block-level compressions, it has a fairly different
 * structure from other file formats.</p>
 * 
 * <p>Block-compressed files don't have a separate position file because
 * it's impossible to jump to each tuple without compression. So, position
 * indexes are included in the compressed file too.</p>
 * 
 * <p>At the end of the file, a 8-byte integer specifies how many blocks
 * this file has. Let the number be n. 3n 8-byte integers precede the end-of-file
 * value. These integers are triplets of the tuple number, byte position and length of each block in this file.
 * For example, "12345,9000000,500000" means that a block starting with 12345-th tuple is located from
 * 9000000-th bytes to 9500000 in the compressed file.</p>
 * 
 * <p>Each block in compressed form is merely a byte array.
 * After decompression, the block is equivalent to an independent column file (FixLen or VarLen).
 * However, each block might have a per-block footer. See the implementation class for more details.</p>
 */
public class LocalBlockCompressionWriter extends LocalRawFileWriter {
    /** compression type for the file. */
    private final CompressionType compressionType;
    /** byte size of uncompressed block to compress together (actual block size might exceed this). */
    protected final int blockSizeInKB;
    /** data of current block before compression. */
    private byte[] currentBlock;
    /** how many bytes of currentBlock are filled out. */
    protected int currentBlockUsed = 0;
    protected long curTuple = 0L;
    private byte[] compressionBuffer;

    /** List of the tuples to start each block. */
    private final ArrayList<Long> blockStartTuples = new ArrayList<Long>();
    /** List of the byte position (in compressed form) of each block. */
    private final ArrayList<Long> blockPositions = new ArrayList<Long>();
    /** List of the byte length (in compressed form) of each block. */
    private final ArrayList<Long> blockLengthes = new ArrayList<Long>();

    public LocalBlockCompressionWriter(File file, CompressionType compressionType) throws IOException {
        super (file, 0); // all writes are batched, so we don't need buffering.
        this.compressionType = compressionType;
        if (compressionType == CompressionType.SNAPPY) {
            blockSizeInKB = 32;
        } else if (compressionType == CompressionType.GZIP_BEST_COMPRESSION) {
            blockSizeInKB = 128;
        } else {
            throw new IOException ("Unexpected compression type for block compression:" + compressionType);
        }
        currentBlock = new byte[(blockSizeInKB << 10) * 12 / 10]; // 20% margin
        compressionBuffer = new byte[(blockSizeInKB << 10) * 12 / 10]; // 20% margin
    }

    /**
     * Proxy object to receive writeXxx from internal FixLen/VarLen writers.
     */
    protected class ProxyValueWriter extends RawValueWriter {
        @Override
        public void writeBytes(byte[] buf, int off, int len) throws IOException {
            // as a very, very, unlucky occasion, it's possible that currentBlock becomes full
            // before we compress and write out the current block.
            // in that case, expand the currentBlock.
            if (currentBlock.length - currentBlockUsed < len) {
                int newSize = (currentBlock.length + len) * 12 / 10; //assure the len with 20% margin
                LOG.warn("As an unlucky (only in terms of performance) incident, we had to expand currentBlock from " + currentBlock.length + " to " + newSize);
                currentBlock = Arrays.copyOf(currentBlock, newSize);
            }
            // simply copy into currentBlock
            System.arraycopy(buf, off, currentBlock, currentBlockUsed, len);
            currentBlockUsed += len;
        }
    }
    /**
     * Check how full the current buffer is, and compress it and write it out
     * if needed.
     */
    public final void flushBlockIfNeeded () throws IOException {
        if (currentBlockUsed >= (blockSizeInKB << 10)) {
            flushBlock();
        }
    }
    /** if the implementation needs a footer for each block, override this. */
    protected void appendBlockFooter () throws IOException {}
    /**
     * Compress and write out current block.
     */
    protected final void flushBlock () throws IOException {
        if (currentBlockUsed == 0) {
            return;
        }
        appendBlockFooter ();
        blockStartTuples.add(curTuple);
        blockPositions.add(getCurPosition());
        int sizeAfterCompression;
        if (compressionType == CompressionType.SNAPPY) {
            sizeAfterCompression = Snappy.compress(currentBlock, 0, currentBlockUsed, compressionBuffer, 0);
            if (sizeAfterCompression > compressionBuffer.length) {
                // this might happen, but not sure how Snappy-java handles exceptional cases..
                throw new IOException ("compresion buffer too small???");
            }
            super.getValueWriter().writeBytes(compressionBuffer, 0, sizeAfterCompression);
        } else {
            assert (compressionType == CompressionType.GZIP_BEST_COMPRESSION);
            RawByteArrayOutputStream rawBuffer = new RawByteArrayOutputStream(compressionBuffer);
            // directly specify level by double brace initialization (kind of a hack)
            GZIPOutputStream gzip = new GZIPOutputStream(rawBuffer) {{
                def.setLevel(Deflater.BEST_COMPRESSION);
            }};
            gzip.flush();
            compressionBuffer = rawBuffer.getRawBuffer(); // in case ByteArrayOutputStream expanded it
            sizeAfterCompression = rawBuffer.size();
            super.getValueWriter().writeBytes(compressionBuffer, 0, sizeAfterCompression);
        }
        blockLengthes.add((long) sizeAfterCompression);
        currentBlockUsed = 0;
    }

    /**
     * Overrides it to compress and write out the current block.
     * @see edu.brown.lasvegas.lvfs.local.LocalRawFileWriter#flush(boolean)
     */
    @Override
    public final void flush (boolean sync) throws IOException {
        flushBlock();
        super.flush(sync);
    }

    /**
     * Extends {@link ByteArrayOutputStream} just for using the given byte array
     * and returning it without copying.
     */
    private static final class RawByteArrayOutputStream extends ByteArrayOutputStream {
        private RawByteArrayOutputStream(byte[] buffer) {
            buf = buffer;
            count = 0;
        }
        private byte[] getRawBuffer () {
            return buf;
        }
    }
}