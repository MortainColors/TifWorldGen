package com.example.tifworldgen.layers;

import com.example.tifworldgen.layers.utils.LayerSecurity;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Band;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class ForestLayer implements AutoCloseable {

    private final String absolutePath;
    private final String mapName;
    private final ThreadLocal<Dataset> threadDataset;
    private final ThreadLocal<Band> threadBandR;
    private final ThreadLocal<Band> threadBandG;
    private final ThreadLocal<Band> threadBandB;
    private final Map<Long, Dataset> openDatasets = new ConcurrentHashMap<>();
    private int tiffWidth;
    private int tiffHeight;
    private Double noDataValue;





    private double dataMax = -1;

    private static final int REGION_SIZE = 512;
    private static final int DEFAULT_FOREST = 0;






    private double scaleFactor = 1.0;

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

    public ForestLayer(String mapName, String absolutePath) {
        this.mapName = mapName;
        this.absolutePath = absolutePath;

        if (isNothing() || isVirtualMap() || absolutePath == null || absolutePath.isEmpty()) {
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
                Band metaBand = metaDataset.GetRasterBand(1);
                Double[] noDataArr = new Double[1];
                metaBand.GetNoDataValue(noDataArr);
                this.noDataValue = noDataArr[0];

                double[] statMin = new double[1], statMax = new double[1], statMean = new double[1], statStd = new double[1];
                if (metaBand.GetStatistics(true, false, statMin, statMax, statMean, statStd) == gdalconst.CE_None) {
                    this.dataMax = statMax[0];
                }
                metaBand.delete(); metaDataset.delete();
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
        if (isNothing() || isVirtualMap() || this.threadDataset == null) return 0;

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
        if (!LayerSecurity.isAuthorizedCaller()) return DEFAULT_FOREST;
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
        int readWidth = Math.min(REGION_SIZE, tiffWidth - startX);
        int readHeight = Math.min(REGION_SIZE, tiffHeight - startZ);

        if (readWidth <= 0 || readHeight <= 0) return regionMask;

        Band bandR = threadBandR.get();
        Band bandG = threadBandG.get();
        Band bandB = threadBandB.get();
        if (bandR == null) return regionMask;

        float[] rBuffer = new float[readWidth * readHeight];
        float[] gBuffer = new float[readWidth * readHeight];
        float[] bBuffer = new float[readWidth * readHeight];

        synchronized (bandR) {
            bandR.ReadRaster(startX, startZ, readWidth, readHeight, rBuffer);
            bandG.ReadRaster(startX, startZ, readWidth, readHeight, gBuffer);
            bandB.ReadRaster(startX, startZ, readWidth, readHeight, bBuffer);
        }

        for (int z = 0; z < readHeight; z++) {
            for (int x = 0; x < readWidth; x++) {
                int bufferIndex = z * readWidth + x;
                if (noDataValue != null && Math.abs(rBuffer[bufferIndex] - noDataValue) < 0.1) continue;
                int r = Math.max(0, Math.min(255, Math.round(rBuffer[bufferIndex])));
                int g = Math.max(0, Math.min(255, Math.round(gBuffer[bufferIndex])));
                int b = Math.max(0, Math.min(255, Math.round(bBuffer[bufferIndex])));
                regionMask[x][z] = (r << 16) | (g << 8) | b;
            }
        }
        return regionMask;
    }

    public void forceClearCache() { maskCache.clear(); }
    public String getPath() { return this.absolutePath; }
    public String getMapName() { return this.mapName; }

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