package com.example.tifworldgen.generators;

import com.example.tifworldgen.layers.utils.HeightMapReader;
import net.minecraft.world.level.block.Blocks;

public class TerrainLayerGenerator implements LayerGenerator {
    private final java.util.function.Supplier<HeightMapReader> readerSupplier;

    public TerrainLayerGenerator(java.util.function.Supplier<HeightMapReader> readerSupplier) {
        this.readerSupplier = readerSupplier;
    }

    @Override
    public void processColumn(int worldX, int worldZ, ColumnData columnData) {
        HeightMapReader heightMapReader = readerSupplier.get();
        int h = heightMapReader != null ? heightMapReader.getHeight(worldX, worldZ) : 64;
        if (h <= 0) h = 64;

        columnData.rawHeight = h;
        columnData.finalHeight = h;
        columnData.waterLevel = h;

        int hL = heightMapReader != null ? heightMapReader.getHeight(worldX - 1, worldZ) : h;
        int hR = heightMapReader != null ? heightMapReader.getHeight(worldX + 1, worldZ) : h;
        int hU = heightMapReader != null ? heightMapReader.getHeight(worldX, worldZ - 1) : h;
        int hD = heightMapReader != null ? heightMapReader.getHeight(worldX, worldZ + 1) : h;

        if (hL <= 0) hL = h; if (hR <= 0) hR = h;
        if (hU <= 0) hU = h; if (hD <= 0) hD = h;

        int gradX = Math.abs(hL - hR);
        int gradZ = Math.abs(hU - hD);

        long edgeHash = worldX * 341873128712L + worldZ * 132897987541L;
        edgeHash ^= edgeHash >>> 13; edgeHash *= 0x5BD1E995L; edgeHash ^= edgeHash >>> 15;
        double slopeJitter = ((edgeHash & 0xFFFFL) / 65535.0 - 0.5) * 1.5;

        if (Math.max(gradX, gradZ) >= 4 + slopeJitter) {
            columnData.surfaceBlock = Blocks.STONE.defaultBlockState();
            columnData.underBlock = Blocks.STONE.defaultBlockState();
        } else {
            columnData.surfaceBlock = Blocks.GRASS_BLOCK.defaultBlockState();
            columnData.underBlock = Blocks.DIRT.defaultBlockState();
        }
    }
}