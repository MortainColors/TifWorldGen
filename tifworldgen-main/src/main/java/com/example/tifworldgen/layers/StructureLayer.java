package com.example.tifworldgen.layers;

import com.example.tifworldgen.layers.utils.LayerSecurity;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Band;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class StructureLayer implements AutoCloseable {

    private final ThreadLocal<Dataset> threadDataset;
    private final ThreadLocal<Band> threadBandR;
    private final ThreadLocal<Band> threadBandG;
    private final ThreadLocal<Band> threadBandB;
    private final Map<Long, Dataset> openDatasets = new ConcurrentHashMap<>();
    private int tiffWidth;
    private int tiffHeight;
    private final String absolutePath;
    private final String mapName;
    private static final int REGION_SIZE = 512;

    private static class CacheEntry {
        final int[][] data;
        long lastAccessTime;
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
            this.threadDataset = null; this.threadBandR = null; this.threadBandG = null; this.threadBandB = null;
            return;
        }

        File tiffFile = new File(absolutePath);
        if (!tiffFile.exists()) {
            this.threadDataset = null; this.threadBandR = null; this.threadBandG = null; this.threadBandB = null;
            return;
        }

        try {
            Dataset metaDataset = gdal.Open(absolutePath, gdalconst.GA_ReadOnly);
            if (metaDataset != null) {
                this.tiffWidth = metaDataset.GetRasterXSize();
                this.tiffHeight = metaDataset.GetRasterYSize();
                metaDataset.delete();
            }
        } catch (Exception e) { e.printStackTrace(); }

        this.threadDataset = ThreadLocal.withInitial(() -> {
            Dataset ds = gdal.Open(absolutePath, gdalconst.GA_ReadOnly);
            if (ds != null) openDatasets.put(Thread.currentThread().getId(), ds);
            return ds;
        });
        this.threadBandR = ThreadLocal.withInitial(() -> {
            Dataset ds = threadDataset.get();
            return ds != null ? ds.GetRasterBand(1) : null;
        });
        this.threadBandG = ThreadLocal.withInitial(() -> {
            Dataset ds = threadDataset.get();
            if (ds != null && ds.GetRasterCount() >= 3) return ds.GetRasterBand(2);
            return threadBandR.get();
        });
        this.threadBandB = ThreadLocal.withInitial(() -> {
            Dataset ds = threadDataset.get();
            if (ds != null && ds.GetRasterCount() >= 3) return ds.GetRasterBand(3);
            return threadBandR.get();
        });
    }

    public String getMapName() { return this.mapName; }
    public String getPath() { return this.absolutePath; }

    public boolean isStructureAllowed(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName) || "Nothing".equalsIgnoreCase(this.mapName)) return true;
        if ("Black Map".equalsIgnoreCase(this.mapName)) return false;
        if (!LayerSecurity.isAuthorizedCaller() || this.threadDataset == null) return true;

        int pixelX = worldX + (tiffWidth / 2);
        int pixelZ = worldZ + (tiffHeight / 2);
        if (pixelX < 0 || pixelX >= tiffWidth || pixelZ < 0 || pixelZ >= tiffHeight) return false;

        return true;
    }

    public String getHexColor(int worldX, int worldZ) {
        if ("White Map".equalsIgnoreCase(this.mapName)) return "#FFFFFF";
        if ("Black Map".equalsIgnoreCase(this.mapName)) return "#000000";
        if (this.threadDataset == null) return "#000000";

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
        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);
        if (readWidth <= 0 || readHeight <= 0) return regionData;

        Band bandR = threadBandR.get();
        Band bandG = threadBandG.get();
        Band bandB = threadBandB.get();
        if (bandR == null) return regionData;

        float[] rBuf = new float[readWidth * readHeight];
        float[] gBuf = new float[readWidth * readHeight];
        float[] bBuf = new float[readWidth * readHeight];

        synchronized (bandR) {
            bandR.ReadRaster(startX, startZ, readWidth, readHeight, rBuf);
            bandG.ReadRaster(startX, startZ, readWidth, readHeight, gBuf);
            bandB.ReadRaster(startX, startZ, readWidth, readHeight, bBuf);
        }

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                int i = z * readWidth + x;
                int r = Math.max(0, Math.min(255, Math.round(rBuf[i])));
                int g = Math.max(0, Math.min(255, Math.round(gBuf[i])));
                int b = Math.max(0, Math.min(255, Math.round(bBuf[i])));
                regionData[x][z] = (r << 16) | (g << 8) | b;
            }
        }
        return regionData;
    }

    public void forceClearCache() { maskCache.clear(); }

    public void close() {
        if (threadBandR != null) threadBandR.remove();
        if (threadBandG != null) threadBandG.remove();
        if (threadBandB != null) threadBandB.remove();
        if (threadDataset != null) threadDataset.remove();
        for (Dataset ds : openDatasets.values()) {
            try { ds.delete(); } catch (Throwable ignored) {}
        }
        openDatasets.clear();
        maskCache.clear();
    }
}