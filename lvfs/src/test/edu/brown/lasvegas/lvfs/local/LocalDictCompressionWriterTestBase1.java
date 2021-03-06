package edu.brown.lasvegas.lvfs.local;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import edu.brown.lasvegas.lvfs.VirtualFile;
import edu.brown.lasvegas.traits.ValueTraits;
import edu.brown.lasvegas.util.ChecksumUtil;

/**
 * Base class of testcases for {@link LocalDictCompressionWriter}.
 * Because some type has limited value domains, some testcase has to be defined in derived classes.
 * See LocalDictCompressionWriterTestBase2 and LocalDictCompressionWriterTestBase4.
 */
public abstract class LocalDictCompressionWriterTestBase1<T extends Comparable<T>, AT> {
    protected final static int COUNT = 75000;
    protected abstract ValueTraits<T, AT> getTraits ();
    protected abstract T getOrderedValue (int order);

    protected int distinctValues;
    protected VirtualFile dataFile, dictFile;
    protected ValueTraits<T, AT> traits;
    protected void init(int distinctValues) throws Exception {
        this.distinctValues = distinctValues;
        this.traits = getTraits();

        dataFile = new LocalVirtualFile("test/local/str_dict.dat");
        if (!dataFile.getParentFile().exists() && !dataFile.getParentFile().mkdirs()) {
            throw new Exception ("Couldn't create test directory " + dataFile.getParentFile().getAbsolutePath());
        }
        dataFile.delete();
        dictFile = new LocalVirtualFile("test/local/str_dict.dat.dict");
        dictFile.delete();
        VirtualFile tmp = new LocalVirtualFile("test/local/str_dict.dat.tmp");
        tmp.delete();
        LocalDictCompressionWriter<T, AT> writer = new LocalDictCompressionWriter<T, AT>(dataFile, dictFile, tmp, traits);
        writer.setCRC32Enabled(true);
        
        Random rand = getDeterministicRandom();
        for (int i = 0; i < COUNT; ++i) {
            writer.writeValue(generateRandom(rand));
        }
        long crc32 = writer.writeFileFooter();
        assertTrue (crc32 != 0);
        writer.flush();
        writer.close();
        long correctCrc32 = ChecksumUtil.getFileCheckSum(dataFile);
        assertEquals (correctCrc32, crc32);
        assertEquals (COUNT, writer.getTupleCount());
    }
    protected static Random getDeterministicRandom () {
        return new Random (22331L); // fixed seed;
    }
    
    protected T generateRandom (Random rand) {
        int r = rand.nextInt();
        if (r < 0) r = -r;
        return getOrderedValue(r % distinctValues);
    }
    @Test
    public void testByteDict() throws Exception {
        init (256);
        assertEquals (1 * COUNT, dataFile.length());
        LocalFixLenReader<Byte, byte[]> reader = LocalFixLenReader.getInstanceTinyint(dataFile);
        LocalDictFile<T, AT> dict = new LocalDictFile<T, AT>(dictFile, traits);
        assertEquals (1, dict.getBytesPerEntry());
        assertTrue (dict.getDictionarySize() <= 256);
        assertTrue (dict.getDictionarySize() > 128);
        Random rand = getDeterministicRandom();
        byte[] buf = new byte[1 << 14];
        int i = 0;
        while (true) {
            int read = reader.readValues(buf, 0, buf.length);
            if (read <= 0) {
                break;
            }
            for (int j = 0; j < read; ++j) {
                T value = dict.decompress(buf[j]);
                T correct = generateRandom(rand);
                assertEquals (correct, value);
            }
            i += read;
        }
        assertEquals (COUNT, i);
        reader.close();
    }

    @Test
    public void testDictCompress() throws Exception {
        this.distinctValues = 256;
        this.traits = getTraits();

        int count = 16;
        AT smallDict = traits.createArray(count);
        for (int i = 0; i < count; ++i) {
        	int value = i * 3 + 2;
        	traits.set(smallDict, i, getOrderedValue(value));
        }
        LocalDictFile<T, AT> dict = new LocalDictFile<T, AT>(smallDict, traits);
        assertEquals (1, dict.getBytesPerEntry());
        assertEquals (count, dict.getDictionarySize());

        for (int i = 0; i < count; ++i) {
        	int value = i * 3 + 2;
        	// test compress()
        	assertEquals (dict.convertDictionaryIndexToCompressedValue(i), dict.compress(getOrderedValue(value)).intValue());
        	assertNull (dict.compress(getOrderedValue(value - 1)));
        	assertNull (dict.compress(getOrderedValue(value - 2)));
        	assertNull (dict.compress(getOrderedValue(value + 1)));
        	assertNull (dict.compress(getOrderedValue(value + 2)));

        	// test compressLower()
        	assertEquals (dict.convertDictionaryIndexToCompressedValue(i), dict.compressLower(getOrderedValue(value)).intValue());
        	assertEquals (dict.convertDictionaryIndexToCompressedValue(i), dict.compressLower(getOrderedValue(value + 1)).intValue());
        	assertEquals (dict.convertDictionaryIndexToCompressedValue(i), dict.compressLower(getOrderedValue(value + 2)).intValue());
        	if (i == 0) {
            	assertNull (dict.compressLower(getOrderedValue(value - 1)));
            	assertNull (dict.compressLower(getOrderedValue(value - 2)));
        	} else {
            	assertEquals (dict.convertDictionaryIndexToCompressedValue(i - 1), dict.compressLower(getOrderedValue(value - 1)).intValue());
            	assertEquals (dict.convertDictionaryIndexToCompressedValue(i - 1), dict.compressLower(getOrderedValue(value - 2)).intValue());
        	}
        }
    }
}
