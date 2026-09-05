package com.example.tifworldgen.layers.utils;

import com.example.tifworldgen.config.ConfigManager;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Band;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeightMapReader implements AutoCloseable {

    private final String absolutePath;
    private final String mapName;
    private Dataset dataset;
    private Band band;

    private int tiffWidth;
    private int tiffHeight;
    private Double noDataValue;

    private double dataMin = 0.0;
    private double dataMax = 1000.0;

    private static final int REGION_SIZE = 512;
    private static final int DEFAULT_RAW = 64;

    private static class CacheEntry {
        final int[][] data;
        long lastAccessTime;
        CacheEntry(int[][] data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    private final Map<Long, CacheEntry> globalCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15000;

    public HeightMapReader(String mapName, String absolutePath) {
        this.mapName = mapName;
        this.absolutePath = absolutePath;

        if (absolutePath == null || absolutePath.isEmpty() || "Nothing".equals(mapName)) {
            this.dataset = null; this.band = null;
            return;
        }

        File tiffFile = new File(absolutePath);
        if (!tiffFile.exists()) {
            this.dataset = null; this.band = null;
            return;
        }

        this.dataset = gdal.Open(absolutePath, gdalconst.GA_ReadOnly);
        if (this.dataset != null) {
            this.tiffWidth = dataset.GetRasterXSize();
            this.tiffHeight = dataset.GetRasterYSize();
            this.band = dataset.GetRasterBand(1);
            Double[] noDataArr = new Double[1];
            band.GetNoDataValue(noDataArr);
            this.noDataValue = noDataArr[0];

            updateDataRange();
        }
    }




    private void updateDataRange() {
        int cfgMin = ConfigManager.configData.terrainBuilder.mapMinHeight;
        int cfgMax = ConfigManager.configData.terrainBuilder.mapMaxHeight;

        if (cfgMin == -999999 || cfgMax == -999999) {
            double[] statMin = new double[1];
            double[] statMax = new double[1];
            double[] statMean = new double[1];
            double[] statStdDev = new double[1];

            int statsResult = band.GetStatistics(true, false, statMin, statMax, statMean, statStdDev);

            if (statsResult == gdalconst.CE_None) {
                this.dataMin = statMin[0];
                this.dataMax = statMax[0];
                System.out.println("[WMAPGEN] Automaticky zistené Min/Max z metadát: " + this.dataMin + " - " + this.dataMax);

                ConfigManager.configData.terrainBuilder.mapMinHeight = (int) this.dataMin;
                ConfigManager.configData.terrainBuilder.mapMaxHeight = (int) this.dataMax;
            } else {
                this.dataMin = 0.0;
                this.dataMax = 255.0;
                System.out.println("[WMAPGEN] Metadáta nenájdené. Predvolený rozsah: 0 - 255");
                ConfigManager.configData.terrainBuilder.mapMinHeight = 0;
                ConfigManager.configData.terrainBuilder.mapMaxHeight = 255;
            }
        } else {
            this.dataMin = cfgMin;
            this.dataMax = cfgMax;
            System.out.println("[WMAPGEN] Používame Min/Max z GUI configu: " + this.dataMin + " - " + this.dataMax);
        }

        if (this.dataMax <= this.dataMin) {
            this.dataMax = this.dataMin + 1.0;
        }
    }

    public boolean isOutOfBounds(int worldX, int worldZ) {
        if (this.dataset == null) return true;
        int pixelX = worldX + (this.tiffWidth / 2);
        int pixelZ = worldZ + (this.tiffHeight / 2);
        return pixelX < 0 || pixelX >= this.tiffWidth || pixelZ < 0 || pixelZ >= this.tiffHeight;
    }

    public int getTiffWidth() { return this.tiffWidth; }
    public int getTiffHeight() { return this.tiffHeight; }

    public int getHeight(int worldX, int worldZ) {
        if (this.dataset == null) return ConfigManager.configData.terrainBuilder.minY - 10;

        int pixelX = worldX + (tiffWidth / 2);
        int pixelZ = worldZ + (tiffHeight / 2);

        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) {
            return ConfigManager.configData.terrainBuilder.minY - 10;
        }

        int regionX = (pixelX / REGION_SIZE) * REGION_SIZE;
        int regionZ = (pixelZ / REGION_SIZE) * REGION_SIZE;

        if (pixelX < 0 && pixelX % REGION_SIZE != 0) regionX -= REGION_SIZE;
        if (pixelZ < 0 && pixelZ % REGION_SIZE != 0) regionZ -= REGION_SIZE;

        long cacheKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);

        if (System.nanoTime() % 1000 == 0) {
            long now = System.currentTimeMillis();
            globalCache.entrySet().removeIf(entry -> (now - entry.getValue().lastAccessTime) > CACHE_TTL_MS);
        }

        CacheEntry entry = globalCache.get(cacheKey);
        if (entry == null) {
            synchronized (this) {
                entry = globalCache.get(cacheKey);
                if (entry == null) {
                    entry = new CacheEntry(loadRegion(regionX, regionZ));
                    globalCache.put(cacheKey, entry);
                }
            }
        }
        entry.lastAccessTime = System.currentTimeMillis();

        int localX = pixelX - regionX;
        int localZ = pixelZ - regionZ;
        return entry.data[localX][localZ];
    }

    private int[][] loadRegion(int startX, int startZ) {
        int[][] regionData = new int[REGION_SIZE][REGION_SIZE];
        int minY = ConfigManager.configData.terrainBuilder.minY;
        int maxY = ConfigManager.configData.terrainBuilder.maxY;

        for (int x = 0; x < REGION_SIZE; x++) {
            for (int z = 0; z < REGION_SIZE; z++) { regionData[x][z] = DEFAULT_RAW; }
        }

        if (this.band == null) return regionData;

        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);
        if (readWidth <= 0 || readHeight <= 0) return regionData;

        float[] buffer = new float[readWidth * readHeight];

        synchronized (this) {
            if (band == null) return regionData;
            int returnCode = band.ReadRaster(startX, startZ, readWidth, readHeight, buffer);
            if (returnCode != gdalconst.CE_None) return regionData;
        }

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                float rawValue = buffer[z * readWidth + x];
                if (Float.isNaN(rawValue) || (noDataValue != null && Math.abs(rawValue - noDataValue) < 0.1)) {
                    regionData[x][z] = minY - 10;
                } else {
                    double normalized = (rawValue - dataMin) / (dataMax - dataMin);
                    if (normalized < 0.0) normalized = 0.0;
                    if (normalized > 1.0) normalized = 1.0;
                    regionData[x][z] = (int) Math.round(minY + (normalized * (maxY - minY)));
                }
            }
        }
        return regionData;
    }

    public String getPath() { return this.absolutePath; }

    public synchronized void close() {
        if (band != null) { band.delete(); band = null; }
        if (dataset != null) { dataset.delete(); dataset = null; }
        globalCache.clear();
    }
}