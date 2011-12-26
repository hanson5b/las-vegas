package edu.brown.lasvegas.lvfs.table;

import java.io.IOException;
import java.io.InputStream;

import edu.brown.lasvegas.ColumnType;
import edu.brown.lasvegas.CompressionType;
import edu.brown.lasvegas.lvfs.TypedReader;

public class BufferedTableReader {
    public BufferedTableReader (ColumnType[] columnTypes, CompressionType[] compressionTypes, int bufferSize, InputStream[] streams) throws IOException {
        this.columnTypes = columnTypes;
        this.compressionTypes = compressionTypes;
        this.buffer = new TupleBuffer (columnTypes, bufferSize);
        this.streams = streams;
    }
    private final ColumnType[] columnTypes;
    private final CompressionType[] compressionTypes;
    private final TupleBuffer buffer;
    private TypedReader<?, ?> columnReaders;
    private InputStream[] streams;
}
