package com.example.tifworldgen.layers;

import com.tifworldgen.lib.tiff.GeoTiffReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class ForestLayer implements AutoCloseable {

    private final String absolutePath;
    private final String mapName;
    private volatile GeoTiffReader reader;
    private volatile int tiffWidth;
    private volatile int tiffHeight;
    private volatile Double noDataValue;
    private volatile double dataMax = -1;

    private double scaleFactor = 1.0;

    private static final int REGION_SIZE = 512;
    private static final int DEFAULT_FOREST = 0;

    private static class CacheEntry {
        final int[][] data;
        volatile long lastAccessTime;
        CacheEntry(int[][] data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    private final Map<Long, CacheEntry> maskCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15000;

    public ForestLayer(String mapName, String absolutePath) {
        this.mapName = mapName;
        this.absolutePath = absolutePath;

        if (isNothing() || isVirtualMap() || absolutePath == null || absolutePath.isEmpty()) {
            this.reader = null;
            return;
        }

        File tiffFile = new File(com.tifworldgen.lib.TifWorldGenLib.resolveMapPath(absolutePath));
        if (!tiffFile.exists()) {
            this.reader = null;
            return;
        }

        try {
            this.reader = GeoTiffReader.open(tiffFile.getAbsolutePath());
            this.tiffWidth = reader.getWidth();
            this.tiffHeight = reader.getHeight();
            this.noDataValue = reader.getNoDataValue();
            double[] stats = reader.computeStatistics(2_000_000);
            if (stats.length == 2) {
                this.dataMax = stats[1];
            }
        } catch (IOException e) {
            System.err.println("[TIFWORLDGEN] Nepodarilo sa otvoriť lesnú mapu '" + absolutePath + "': " + e.getMessage());
            closeQuietly();
            this.reader = null;
        }
    }

    private void closeQuietly() {
        if (reader != null) {
            try { reader.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isNothing() {
        return this.mapName == null || this.mapName.isEmpty() || "Nothing".equalsIgnoreCase(this.mapName) || "Black Map".equalsIgnoreCase(this.mapName);
    }

    public boolean isVirtualMap() {
        return "White Map".equalsIgnoreCase(this.mapName) || "Black Map".equalsIgnoreCase(this.mapName);
    }

    public void setScale(double scale) {
        if (scale > 0.0) this.scaleFactor = scale;
    }

    public int getTiffWidth() { return tiffWidth; }
    public int getTiffHeight() { return tiffHeight; }

    private int getRgbAt(int worldX, int worldZ) {
        if (isNothing() || isVirtualMap() || this.reader == null) return 0;

        int pixelX = (int) Math.floor(worldX / scaleFactor) + (tiffWidth / 2);
        int pixelZ = (int) Math.floor(worldZ / scaleFactor) + (tiffHeight / 2);

        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) return 0;

        int regionX = (pixelX / REGION_SIZE) * REGION_SIZE;
        int regionZ = (pixelZ / REGION_SIZE) * REGION_SIZE;
        long cacheKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);

        CacheEntry entry = maskCache.get(cacheKey);
        if (entry == null) {
            synchronized (this) {
                entry = maskCache.get(cacheKey);
                if (entry == null) {
                    entry = new CacheEntry(loadForestRegion(regionX, regionZ));
                    maskCache.put(cacheKey, entry);
                }
            }
        }
        return entry.data[pixelX - regionX][pixelZ - regionZ];
    }

    public String getHexColor(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return "#FFFFFF";
        if (isNothing()) return "#000000";
        int rgb = getRgbAt(worldX, worldZ);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        if (dataMax > 0 && dataMax < 64) {
            r = clamp255((int) Math.round(r / dataMax * 255.0));
            g = clamp255((int) Math.round(g / dataMax * 255.0));
            b = clamp255((int) Math.round(b / dataMax * 255.0));
        }
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    public int getRawPixelValue(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return 1;
        if (isNothing()) return 0;
        return getRgbAt(worldX, worldZ) & 0xFF;
    }

    public int getForestType(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return 1;
        if (isNothing()) return 0;
        int rgb = getRgbAt(worldX, worldZ);
        int brightness = (int) Math.round(((rgb >> 16) & 0xFF) * 0.299 + ((rgb >> 8) & 0xFF) * 0.587 + (rgb & 0xFF) * 0.114);
        if (dataMax > 0 && dataMax < 64) {
            brightness = clamp255((int) Math.round(brightness / dataMax * 255.0));
        }
        if (brightness > 200) return 2;
        if (brightness > 50) return 1;
        return rgb & 0xFF;
    }

    private int[][] loadForestRegion(int startX, int startZ) {
        int[][] regionMask = new int[REGION_SIZE][REGION_SIZE];
        GeoTiffReader r = this.reader;
        if (r == null) return regionMask;

        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);
        if (readWidth <= 0 || readHeight <= 0) return regionMask;

        int bands = Math.max(1, r.getSamplesPerPixel());
        float[][] buf = new float[Math.min(bands, 3)][];
        for (int b = 0; b < buf.length; b++) {
            buf[b] = new float[readWidth * readHeight];
            try {
                r.readRegion(b, startX, startZ, readWidth, readHeight, buf[b]);
            } catch (IOException e) {
                System.err.println("[TIFWORLDGEN] Čítanie regiónu lesnej mapy zlyhalo: " + e.getMessage());
                return regionMask;
            }
        }

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                int bufferIndex = z * readWidth + x;
                float rv = buf[0][bufferIndex];
                if (noDataValue != null && Math.abs(rv - noDataValue) < 0.1) continue;
                int ri = clamp255(Math.round(rv));
                int gi = buf.length > 1 ? clamp255(Math.round(buf[1][bufferIndex])) : ri;
                int bi = buf.length > 2 ? clamp255(Math.round(buf[2][bufferIndex])) : ri;
                regionMask[x][z] = (ri << 16) | (gi << 8) | bi;
            }
        }
        return regionMask;
    }

    public String getPath() { return this.absolutePath; }
    public String getMapName() { return this.mapName; }

    public void close() {
        closeQuietly();
        this.reader = null;
        maskCache.clear();
    }
}
