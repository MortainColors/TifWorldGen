package com.example.tifworldgen.layers;

import com.tifworldgen.lib.tiff.GeoTiffReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class StructureLayer implements AutoCloseable {

    private volatile GeoTiffReader reader;
    private volatile int tiffWidth;
    private volatile int tiffHeight;
    private final String absolutePath;
    private final String mapName;
    private static final int REGION_SIZE = 512;

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

    public StructureLayer(String mapName, String absolutePath) {
        this.mapName = mapName;
        this.absolutePath = absolutePath;

        if ("White Map".equalsIgnoreCase(mapName) || "Black Map".equalsIgnoreCase(mapName) || "Nothing".equalsIgnoreCase(mapName) || absolutePath == null || absolutePath.isEmpty()) {
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
        } catch (IOException e) {
            System.err.println("[TIFWORLDGEN] Nepodarilo sa otvoriť štruktúrnu mapu '" + absolutePath + "': " + e.getMessage());
            closeQuietly();
            this.reader = null;
        }
    }

    private void closeQuietly() {
        if (reader != null) {
            try { reader.close(); } catch (IOException ignored) {}
        }
    }

    public String getMapName() { return this.mapName; }
    public String getPath() { return this.absolutePath; }

    public boolean isStructureAllowed(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName) || "Nothing".equalsIgnoreCase(this.mapName)) return true;
        if ("Black Map".equalsIgnoreCase(this.mapName)) return false;
        if (this.reader == null) return true;

        int pixelX = worldX + (tiffWidth / 2);
        int pixelZ = worldZ + (tiffHeight / 2);
        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) return false;

        return true;
    }

    public String getHexColor(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return "#FFFFFF";
        if ("Black Map".equalsIgnoreCase(this.mapName)) return "#000000";
        if (this.reader == null) return "#000000";

        int pixelX = worldX + (tiffWidth / 2);
        int pixelZ = worldZ + (tiffHeight / 2);
        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) return "#000000";

        int regionX = (pixelX / REGION_SIZE) * REGION_SIZE;
        int regionZ = (pixelZ / REGION_SIZE) * REGION_SIZE;
        if (pixelX < 0 && pixelX % REGION_SIZE != 0) regionX -= REGION_SIZE;
        if (pixelZ < 0 && pixelZ % REGION_SIZE != 0) regionZ -= REGION_SIZE;

        long cacheKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        CacheEntry entry = maskCache.get(cacheKey);
        if (entry == null) {
            synchronized (this) {
                entry = maskCache.get(cacheKey);
                if (entry == null) {
                    entry = new CacheEntry(loadStructureRegion(regionX, regionZ));
                    maskCache.put(cacheKey, entry);
                }
            }
        }

        int rgb = entry.data[pixelX - regionX][pixelZ - regionZ];
        return String.format("#%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private int[][] loadStructureRegion(int startX, int startZ) {
        int[][] regionData = new int[REGION_SIZE][REGION_SIZE];
        GeoTiffReader r = this.reader;
        if (r == null) return regionData;

        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);
        if (readWidth <= 0 || readHeight <= 0) return regionData;

        int bands = Math.max(1, r.getSamplesPerPixel());
        float[][] buf = new float[Math.min(bands, 3)][];
        for (int b = 0; b < buf.length; b++) {
            buf[b] = new float[readWidth * readHeight];
            try {
                r.readRegion(b, startX, startZ, readWidth, readHeight, buf[b]);
            } catch (IOException e) {
                System.err.println("[TIFWORLDGEN] Čítanie regiónu štruktúrnej mapy zlyhalo: " + e.getMessage());
                return regionData;
            }
        }

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                int i = z * readWidth + x;
                int ri = Math.max(0, Math.min(255, Math.round(buf[0][i])));
                int gi = buf.length > 1 ? Math.max(0, Math.min(255, Math.round(buf[1][i]))) : ri;
                int bi = buf.length > 2 ? Math.max(0, Math.min(255, Math.round(buf[2][i]))) : ri;
                regionData[x][z] = (ri << 16) | (gi << 8) | bi;
            }
        }
        return regionData;
    }

    public void close() {
        closeQuietly();
        this.reader = null;
        maskCache.clear();
    }
}
