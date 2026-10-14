package com.tifworldgen.lib.tiff;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class GeoTiffReader implements AutoCloseable {

    private static final int TAG_IMAGE_WIDTH = 256;
    private static final int TAG_IMAGE_LENGTH = 257;
    private static final int TAG_BITS_PER_SAMPLE = 258;
    private static final int TAG_COMPRESSION = 259;
    private static final int TAG_STRIP_OFFSETS = 273;
    private static final int TAG_SAMPLES_PER_PIXEL = 277;
    private static final int TAG_ROWS_PER_STRIP = 278;
    private static final int TAG_STRIP_BYTE_COUNTS = 279;
    private static final int TAG_PLANAR_CONFIG = 284;
    private static final int TAG_TILE_WIDTH = 322;
    private static final int TAG_TILE_LENGTH = 323;
    private static final int TAG_TILE_OFFSETS = 324;
    private static final int TAG_TILE_BYTE_COUNTS = 325;
    private static final int TAG_SAMPLE_FORMAT = 339;
    private static final int TAG_NODATA = 42113;

    private final FileChannel channel;
    private final boolean littleEndian;
    private final boolean bigTiff;

    private final int width;
    private final int height;
    private final int samplesPerPixel;
    private final int bitsPerSample;
    private final int sampleFormat;
    private final int compression;
    private final boolean tiled;
    private final int blockWidth;
    private final int blockHeight;
    private final long[] blockOffsets;
    private final long[] blockByteCounts;
    private final Double noDataValue;

    private final int bytesPerSample;

    private final LinkedHashMap<Long, float[][]> blockCache;
    private long cacheBytes = 0;
    private final long cacheMaxBytes;

    public static GeoTiffReader open(String path) throws IOException {
        return new GeoTiffReader(Path.of(path), 64L * 1024 * 1024);
    }

    public static GeoTiffReader open(String path, long cacheMaxBytes) throws IOException {
        return new GeoTiffReader(Path.of(path), cacheMaxBytes);
    }

    private GeoTiffReader(Path path, long cacheMaxBytes) throws IOException {
        this.cacheMaxBytes = cacheMaxBytes;
        this.channel = FileChannel.open(path, StandardOpenOption.READ);

        ByteBuffer header = readFully(16, 0);
        int byteOrderMark = header.getShort() & 0xFFFF;
        if (byteOrderMark == 0x4949) {
            this.littleEndian = true;
        } else if (byteOrderMark == 0x4D4D) {
            this.littleEndian = false;
        } else {
            throw new IOException("Not a TIFF file (bad byte order mark): " + path);
        }
        header.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        int version = header.getShort() & 0xFFFF;
        if (version == 42) {
            this.bigTiff = false;
        } else if (version == 43) {
            this.bigTiff = true;
            int offsetSize = header.getShort() & 0xFFFF;
            if (offsetSize != 8) throw new IOException("Unsupported BigTIFF offset size: " + offsetSize);
            header.getShort();
        } else {
            throw new IOException("Unsupported TIFF version: " + version);
        }

        long ifdOffset = bigTiff ? header.getLong() : (header.getInt() & 0xFFFFFFFFL);
        Ifd ifd = new Ifd(ifdOffset);

        this.width = (int) ifd.longValue(TAG_IMAGE_WIDTH, -1);
        this.height = (int) ifd.longValue(TAG_IMAGE_LENGTH, -1);
        if (width <= 0 || height <= 0) throw new IOException("Missing image dimensions");

        this.samplesPerPixel = (int) ifd.longValue(TAG_SAMPLES_PER_PIXEL, 1);
        this.sampleFormat = (int) ifd.longValue(TAG_SAMPLE_FORMAT, 1);
        this.compression = (int) ifd.longValue(TAG_COMPRESSION, 1);

        this.bitsPerSample = (int) ifd.longValue(TAG_BITS_PER_SAMPLE, 8);
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 32) {
            throw new IOException("Unsupported BitsPerSample: " + bitsPerSample);
        }
        this.bytesPerSample = bitsPerSample / 8;

        int planarConfig = (int) ifd.longValue(TAG_PLANAR_CONFIG, 1);
        if (planarConfig != 1) {
            throw new IOException("Unsupported PlanarConfiguration (only chunky/1 supported, found " + planarConfig + ")");
        }

        this.tiled = ifd.has(TAG_TILE_OFFSETS);
        if (tiled) {
            this.blockWidth = (int) ifd.longValue(TAG_TILE_WIDTH, -1);
            this.blockHeight = (int) ifd.longValue(TAG_TILE_LENGTH, -1);
            this.blockOffsets = ifd.longArray(TAG_TILE_OFFSETS);
            this.blockByteCounts = ifd.longArray(TAG_TILE_BYTE_COUNTS);
        } else {
            this.blockWidth = width;
            long rpsRaw = ifd.longValue(TAG_ROWS_PER_STRIP, height);
            int rps = (int) Math.min(rpsRaw <= 0 ? height : rpsRaw, height);
            if (rps <= 0) rps = height;
            this.blockHeight = rps;
            this.blockOffsets = ifd.longArray(TAG_STRIP_OFFSETS);
            this.blockByteCounts = ifd.longArray(TAG_STRIP_BYTE_COUNTS);
        }

        if (blockOffsets == null || blockByteCounts == null || blockOffsets.length == 0) {
            throw new IOException("Missing strip/tile offset tables");
        }
        if (blockOffsets.length != blockByteCounts.length) {
            throw new IOException("Offset/byte-count table length mismatch");
        }

        String nodataAscii = ifd.asciiValue(TAG_NODATA);
        Double parsedNoData = null;
        if (nodataAscii != null) {
            try {
                parsedNoData = Double.parseDouble(nodataAscii.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        this.noDataValue = parsedNoData;

        this.blockCache = new LinkedHashMap<>(64, 0.75f, true);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSamplesPerPixel() { return samplesPerPixel; }
    public Double getNoDataValue() { return noDataValue; }
    public boolean isTiled() { return tiled; }
    public int getCompression() { return compression; }
    public int getBlockWidth() { return blockWidth; }
    public int getBlockHeight() { return blockHeight; }

    public synchronized void readRegion(int band, int xOff, int yOff, int w, int h, float[] dest) throws IOException {
        if (band < 0 || band >= samplesPerPixel) throw new IllegalArgumentException("Band out of range: " + band);
        if (dest.length < (long) w * h) throw new IllegalArgumentException("Destination buffer too small");
        int colBlockW = tiled ? blockWidth : width;
        for (int row = 0; row < h; row++) {
            int y = yOff + row;
            if (y < 0 || y >= height) {
                for (int i = 0; i < w; i++) dest[row * w + i] = 0f;
                continue;
            }
            int blockRow = y / blockHeight;
            int inY = y % blockHeight;
            for (int col = 0; col < w; ) {
                int x = xOff + col;
                if (x < 0 || x >= width) {
                    dest[row * w + col] = 0f;
                    col++;
                    continue;
                }
                int blockCol = x / colBlockW;
                int inX = x % colBlockW;
                int blockIndex = blockRow * blocksAcross() + blockCol;
                int runLen = Math.min(Math.min(w - col, colBlockW - inX), width - x);
                float[][] bands = getBlock(blockIndex);
                float[] src = bands[band];
                int srcPos = inY * colBlockW + inX;
                System.arraycopy(src, srcPos, dest, row * w + col, runLen);
                col += runLen;
            }
        }
    }

    public synchronized double[] computeStatistics(int maxSamples) throws IOException {
        int colBlockW = tiled ? blockWidth : width;
        long blockPixels = (long) colBlockW * blockHeight;
        long maxBlocksToRead = Math.max(1, maxSamples / Math.max(1, blockPixels));
        int blockStride = (int) Math.max(1, blockOffsets.length / maxBlocksToRead);
        long pixelStride = Math.max(1, blockPixels / Math.max(1, maxSamples / Math.max(1, maxBlocksToRead)));
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long counted = 0;
        for (int b = 0; b < blockOffsets.length; b += blockStride) {
            float[][] bands;
            try {
                bands = getBlock(b);
            } catch (IOException e) {
                continue;
            }
            float[] data = bands[0];
            int pixelsInBlock = rowsInBlock(b) * colBlockW;
            for (long p = 0; p < pixelsInBlock; p += pixelStride) {
                float v = data[(int) p];
                if (Float.isNaN(v)) continue;
                if (noDataValue != null && Math.abs((double) v - noDataValue) < 0.1d) continue;
                if (v < min) min = v;
                if (v > max) max = v;
                counted++;
            }
            if (counted >= maxSamples) break;
        }
        if (counted == 0) return new double[]{0.0, 0.0};
        return new double[]{min, max};
    }

    private int blocksAcross() {
        return tiled ? (width + blockWidth - 1) / blockWidth : 1;
    }

    private int rowsInBlock(int blockIndex) {
        int blockRow = tiled ? blockIndex / blocksAcross() : blockIndex;
        long firstRow = (long) blockRow * blockHeight;
        return (int) Math.max(1, Math.min(blockHeight, height - firstRow));
    }

    private float[][] getBlock(long blockIndex) throws IOException {
        float[][] cached = blockCache.get(blockIndex);
        if (cached != null) return cached;

        int idx = (int) blockIndex;
        long offset = blockOffsets[idx];
        long byteCount = blockByteCounts[idx];
        if (byteCount <= 0) throw new EOFException("Empty block " + blockIndex);

        int colBlockW = tiled ? blockWidth : width;
        int rowsInBlock = rowsInBlock(idx);
        int pixelsInBlock = colBlockW * rowsInBlock;
        int rawSize = pixelsInBlock * samplesPerPixel * bytesPerSample;

        byte[] raw;
        if (compression == 1) {
            if (byteCount < rawSize) throw new EOFException("Block " + blockIndex + " too small: " + byteCount + " < " + rawSize);
            ByteBuffer buf = readFully(rawSize, offset);
            raw = new byte[rawSize];
            buf.get(raw);
        } else if (compression == 8 || compression == 32946) {
            raw = inflate(readBytes(byteCount, offset), rawSize);
        } else if (compression == 5) {
            raw = lzwDecode(readBytes(byteCount, offset), rawSize);
        } else {
            throw new IOException("Unsupported TIFF compression " + compression + " (supported: 1=none, 8/32946=Deflate, 5=LZW). Re-save the map uncompressed or Deflate.");
        }

        float[][] bands = new float[samplesPerPixel][];
        ByteBuffer bb = ByteBuffer.wrap(raw);
        bb.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (samplesPerPixel == 1) {
            float[] data = new float[pixelsInBlock];
            for (int p = 0; p < pixelsInBlock; p++) data[p] = readSample(bb);
            bands[0] = data;
        } else {
            for (int b = 0; b < samplesPerPixel; b++) bands[b] = new float[pixelsInBlock];
            for (int p = 0; p < pixelsInBlock; p++) {
                for (int b = 0; b < samplesPerPixel; b++) bands[b][p] = readSample(bb);
            }
        }

        long entryBytes = (long) pixelsInBlock * samplesPerPixel * 4L;
        while (cacheBytes + entryBytes > cacheMaxBytes && !blockCache.isEmpty()) {
            Long eldest = blockCache.keySet().iterator().next();
            float[][] evicted = blockCache.remove(eldest);
            cacheBytes -= (long) evicted[0].length * samplesPerPixel * 4L;
        }
        if (cacheBytes + entryBytes <= cacheMaxBytes) {
            blockCache.put(blockIndex, bands);
            cacheBytes += entryBytes;
        }
        return bands;
    }

    private float readSample(ByteBuffer bb) {
        if (bitsPerSample == 8) {
            int v = bb.get() & 0xFF;
            return sampleFormat == 2 ? (byte) v : v;
        } else if (bitsPerSample == 16) {
            int v = bb.getShort() & 0xFFFF;
            return sampleFormat == 2 ? (short) v : v;
        } else {
            if (sampleFormat == 3) return Float.intBitsToFloat(bb.getInt());
            return bb.getInt();
        }
    }

    private byte[] readBytes(long byteCount, long offset) throws IOException {
        if (byteCount > Integer.MAX_VALUE) throw new IOException("Block too large: " + byteCount);
        ByteBuffer buf = readFully((int) byteCount, offset);
        byte[] out = new byte[(int) byteCount];
        buf.get(out);
        return out;
    }

    private static byte[] inflate(byte[] compressed, int expectedSize) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        byte[] out = new byte[expectedSize];
        try {
            int written = 0;
            while (written < expectedSize) {
                int n = inflater.inflate(out, written, expectedSize - written);
                if (n == 0) {
                    if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) break;
                }
                written += n;
            }
            if (written < expectedSize) throw new EOFException("Deflate block truncated: " + written + " < " + expectedSize);
            return out;
        } catch (DataFormatException e) {
            throw new IOException("Corrupt Deflate data", e);
        } finally {
            inflater.end();
        }
    }

    private static byte[] lzwDecode(byte[] input, int expectedSize) throws IOException {
        byte[] output = new byte[expectedSize];
        int outPos = 0;
        int[][] table = new int[4096][];
        for (int i = 0; i < 256; i++) table[i] = new int[]{i};
        int nextCode = 258;
        int codeWidth = 9;
        int bitPos = 0;
        int prev = -1;

        while (outPos < expectedSize) {
            if ((bitPos + codeWidth) > input.length * 8) break;
            int code = 0;
            for (int i = 0; i < codeWidth; i++) {
                int bit = (input[(bitPos + i) >> 3] >> (7 - ((bitPos + i) & 7))) & 1;
                code = (code << 1) | bit;
            }
            bitPos += codeWidth;

            if (code == 257) break;
            if (code == 256) {
                nextCode = 258;
                codeWidth = 9;
                prev = -1;
                continue;
            }

            int[] entry;
            if (code < 256 || (code < nextCode && table[code] != null)) {
                entry = table[code];
            } else if (code == nextCode && prev >= 0 && table[prev] != null) {
                int[] prevEntry = table[prev];
                entry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, entry, 0, prevEntry.length);
                entry[prevEntry.length] = prevEntry[0];
            } else {
                throw new IOException("Corrupt LZW stream (code " + code + ", next " + nextCode + ")");
            }

            for (int b : entry) {
                if (outPos >= expectedSize) break;
                output[outPos++] = (byte) b;
            }

            if (prev >= 0 && nextCode < 4096) {
                int[] prevEntry = table[prev];
                int[] newEntry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, newEntry, 0, prevEntry.length);
                newEntry[prevEntry.length] = entry[0];
                table[nextCode++] = newEntry;
                if (nextCode + 1 >= (1 << codeWidth) && codeWidth < 12) {
                    codeWidth++;
                }
            }
            prev = code;
        }
        if (outPos < expectedSize) throw new EOFException("LZW block truncated: " + outPos + " < " + expectedSize);
        return output;
    }

    private ByteBuffer readFully(int size, long position) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(size);
        int read = 0;
        while (read < size) {
            int n = channel.read(buf, position + read);
            if (n < 0) throw new EOFException("Unexpected end of file at " + (position + read));
            read += n;
        }
        buf.flip();
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        return buf;
    }

    @Override
    public synchronized void close() throws IOException {
        blockCache.clear();
        cacheBytes = 0;
        channel.close();
    }

    private final class Ifd {
        final long[] tags;
        final long[] counts;
        final long[] types;
        final long[] values;
        final boolean[] isOffset;

        Ifd(long offset) throws IOException {
            int entriesFieldSize = bigTiff ? 8 : 2;
            ByteBuffer head = readFully(entriesFieldSize, offset);
            int entries = bigTiff ? (int) head.getLong() : (head.getShort() & 0xFFFF);
            if (entries < 0 || entries > 100000) throw new IOException("Implausible IFD entry count: " + entries);
            int entrySize = bigTiff ? 20 : 12;
            ByteBuffer data = readFully(entries * entrySize, offset + entriesFieldSize);
            tags = new long[entries];
            types = new long[entries];
            counts = new long[entries];
            values = new long[entries];
            isOffset = new boolean[entries];
            for (int i = 0; i < entries; i++) {
                tags[i] = data.getShort() & 0xFFFF;
                types[i] = data.getShort() & 0xFFFF;
                counts[i] = bigTiff ? data.getLong() : (data.getInt() & 0xFFFFFFFFL);
                values[i] = bigTiff ? data.getLong() : (data.getInt() & 0xFFFFFFFFL);
                int typeSize = typeSize((int) types[i]);
                long byteLen = counts[i] * typeSize;
                isOffset[i] = byteLen > (bigTiff ? 8 : 4);
            }
        }

        boolean has(int tag) {
            for (int i = 0; i < tags.length; i++) if (tags[i] == tag) return true;
            return false;
        }

        long longValue(int tag, long def) throws IOException {
            for (int i = 0; i < tags.length; i++) {
                if (tags[i] == tag) {
                    if (counts[i] < 1) return def;
                    ByteBuffer buf = tagBuffer(i, typeSize((int) types[i]));
                    return readScalar(buf, (int) types[i]);
                }
            }
            return def;
        }

        long[] longArray(int tag) throws IOException {
            for (int i = 0; i < tags.length; i++) {
                if (tags[i] == tag) {
                    int typeSize = typeSize((int) types[i]);
                    long byteLen = counts[i] * typeSize;
                    if (byteLen > Integer.MAX_VALUE || byteLen < 0) throw new IOException("Tag " + tag + " too large");
                    ByteBuffer buf = tagBuffer(i, (int) byteLen);
                    long[] out = new long[(int) counts[i]];
                    for (int j = 0; j < counts[i]; j++) out[j] = readScalar(buf, (int) types[i]);
                    return out;
                }
            }
            return null;
        }

        String asciiValue(int tag) throws IOException {
            for (int i = 0; i < tags.length; i++) {
                if (tags[i] == tag) {
                    int typeSize = typeSize((int) types[i]);
                    long byteLen = counts[i] * typeSize;
                    if (byteLen > Integer.MAX_VALUE || byteLen < 0) return null;
                    ByteBuffer buf = tagBuffer(i, (int) byteLen);
                    byte[] bytes = new byte[(int) byteLen];
                    buf.get(bytes);
                    return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
                }
            }
            return null;
        }

        private ByteBuffer tagBuffer(int i, int byteLen) throws IOException {
            if (isOffset[i]) {
                return readFully(byteLen, values[i]);
            }
            byte[] bytes = new byte[8];
            ByteBuffer tmp = ByteBuffer.wrap(bytes);
            if (littleEndian) {
                tmp.order(ByteOrder.LITTLE_ENDIAN).putLong(values[i]);
            } else {
                long shifted = byteLen >= 8 ? values[i] : (values[i] << (64 - 8L * byteLen));
                tmp.order(ByteOrder.BIG_ENDIAN).putLong(shifted);
            }
            ByteBuffer out = ByteBuffer.wrap(bytes, 0, byteLen);
            out.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            return out;
        }

        private long readScalar(ByteBuffer buf, int type) {
            switch (type) {
                case 1: case 2: case 6: case 7: return buf.get() & 0xFF;
                case 3: case 8: return buf.getShort() & 0xFFFF;
                case 4: case 9: case 13: return buf.getInt() & 0xFFFFFFFFL;
                case 5: case 10: {
                    long num = buf.getInt() & 0xFFFFFFFFL;
                    long den = buf.getInt() & 0xFFFFFFFFL;
                    return den == 0 ? 0 : Math.round((double) num / den);
                }
                case 11: return (long) buf.getFloat();
                case 12: return (long) buf.getDouble();
                case 16: case 17: case 18: return buf.getLong();
                default: return 0;
            }
        }
    }

    private static int typeSize(int type) {
        switch (type) {
            case 1: case 2: case 6: case 7: return 1;
            case 3: case 8: return 2;
            case 4: case 9: case 11: case 13: return 4;
            case 5: case 10: case 12: case 16: case 17: case 18: return 8;
            default: return 1;
        }
    }
}
