package com.example.tifworldgen.layers;

import com.example.tifworldgen.layers.utils.LayerSecurity;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Band;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class WaterLayer implements AutoCloseable {

    private volatile Dataset dataset;
    private volatile Band band;
    private int tiffWidth;
    private int tiffHeight;
    private Double noDataValue;
    private final String absolutePath;
    private final String mapName;
    private static final int REGION_SIZE = 512;

    private static class CacheEntry {
        final boolean[][] data;
        long lastAccessTime;

        CacheEntry(boolean[][] data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    private final Map<Long, CacheEntry> maskCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15000;

    public WaterLayer(String mapName, String absolutePath) {
        this.mapName = mapName;
        this.absolutePath = absolutePath;

        if ("White Map".equalsIgnoreCase(mapName) || "Black Map".equalsIgnoreCase(mapName) || "Nothing".equalsIgnoreCase(mapName) || absolutePath == null || absolutePath.isEmpty()) {
            this.dataset = null;
            this.band = null;
            return;
        }

        File tiffFile = new File(absolutePath);
        if (!tiffFile.exists()) {
            this.dataset = null;
            this.band = null;
            return;
        }

        gdal.AllRegister();
        this.dataset = gdal.Open(absolutePath, gdalconst.GA_ReadOnly);
        if (this.dataset == null) return;

        this.band = dataset.GetRasterBand(1);
        this.tiffWidth = dataset.GetRasterXSize();
        this.tiffHeight = dataset.GetRasterYSize();

        Double[] noDataArr = new Double[1];
        band.GetNoDataValue(noDataArr);
        this.noDataValue = noDataArr[0];
    }

    public boolean isWater(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return true;
        if ("Black Map".equalsIgnoreCase(this.mapName) || "Nothing".equalsIgnoreCase(this.mapName))
            return false;

        if (!LayerSecurity.isAuthorizedCaller() || this.dataset == null || this.band == null) {
            return false;
        }

        int pixelX = worldX + (tiffWidth / 2);
        int pixelZ = worldZ + (tiffHeight / 2);

        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) {
            return false;
        }

        int regionX = (pixelX / REGION_SIZE) * REGION_SIZE;
        int regionZ = (pixelZ / REGION_SIZE) * REGION_SIZE;

        if (pixelX < 0 && pixelX % REGION_SIZE != 0) regionX -= REGION_SIZE;
        if (pixelZ < 0 && pixelZ % REGION_SIZE != 0) regionZ -= REGION_SIZE;

        long cacheKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);

        if (System.nanoTime() % 1000 == 0) {
            long now = System.currentTimeMillis();
            maskCache.entrySet().removeIf(entry -> (now - entry.getValue().lastAccessTime) > CACHE_TTL_MS);
        }

        CacheEntry entry = maskCache.get(cacheKey);
        if (entry == null) {
            boolean[][] newData = loadWaterRegion(regionX, regionZ);
            entry = new CacheEntry(newData);
            maskCache.put(cacheKey, entry);
        } else {
            entry.lastAccessTime = System.currentTimeMillis();
        }

        return entry.data[pixelX - regionX][pixelZ - regionZ];
    }

    private boolean[][] loadWaterRegion(int startX, int startZ) {
        boolean[][] regionMask = new boolean[REGION_SIZE][REGION_SIZE];
        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);

        if (readWidth <= 0 || readHeight <= 0) return regionMask;

        float[] buffer = new float[readWidth * readHeight];
        int returnCode;

        synchronized (this) {
            if (band == null) return regionMask;
            returnCode = band.ReadRaster(startX, startZ, readWidth, readHeight, buffer);
        }

        if (returnCode != gdalconst.CE_None) return regionMask;

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                int bufferIndex = z * readWidth + x;
                float value = buffer[bufferIndex];

                if (Float.isNaN(value) || (noDataValue != null && Math.abs(value - noDataValue) < 0.1)) {
                    regionMask[x][z] = false;
                    continue;
                }
                regionMask[x][z] = (value > 127f);
            }
        }

        return regionMask;
    }

    public String getMapName() {
        return this.mapName;
    }

    public void forceClearCache() {
        maskCache.clear();
    }

    public String getPath() {
        return this.absolutePath;
    }

    public synchronized void close() {
        if (band != null) {
            band.delete();
            band = null;
        }
        if (dataset != null) {
            dataset.delete();
            dataset = null;
        }
        maskCache.clear();
    }
}