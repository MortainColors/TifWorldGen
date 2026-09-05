package com.example.tifworldgen.generators;

import com.example.tifworldgen.layers.WaterLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class WaterLayerGenerator implements LayerGenerator {
    private final WaterLayer waterLayer;

    public WaterLayerGenerator(WaterLayer waterLayer) {
        this.waterLayer = waterLayer;
    }

    private double getOrganicNoise(int x, int z, double scale) {
        double nx = x * scale;
        double nz = z * scale;
        double noise = Math.sin(nx) * Math.cos(nz);
        noise += 0.5 * Math.sin(nx * 1.732 + 4.2) * Math.cos(nz * 2.193 - 1.1);
        noise += 0.25 * Math.sin(nx * 3.141 - 0.5) * Math.sin(nz * 2.718 + 0.8);
        return noise / 1.75;
    }

    private BlockState getRiverbedBlockState(int x, int z) {
        double bedNoise = getOrganicNoise(x, z, 0.1);
        if (bedNoise > 0.5) return Blocks.SAND.defaultBlockState();
        if (bedNoise < -0.5) return Blocks.CLAY.defaultBlockState();
        if (bedNoise > 0.2) return Blocks.GRAVEL.defaultBlockState();
        return Blocks.DIRT.defaultBlockState();
    }

    @Override
    public void processColumn(int worldX, int worldZ, ColumnData columnData) {
        boolean isWaterMask = waterLayer.isWater(worldX, worldZ);

        if (isWaterMask) {
            columnData.isWater = true;

            int waterNeighbors = 0;
            if (waterLayer.isWater(worldX + 1, worldZ)) waterNeighbors++;
            if (waterLayer.isWater(worldX - 1, worldZ)) waterNeighbors++;
            if (waterLayer.isWater(worldX, worldZ + 1)) waterNeighbors++;
            if (waterLayer.isWater(worldX, worldZ - 1)) waterNeighbors++;

            double deepNoise = getOrganicNoise(worldX, worldZ, 0.07);
            if (waterNeighbors >= 4) {
                columnData.waterDepth = Math.max(2, Math.min(3, 2 + (int) Math.round(deepNoise)));
            } else if (waterNeighbors == 3) {
                columnData.waterDepth = Math.max(1, Math.min(2, 1 + (int) Math.round(deepNoise)));
            } else {
                columnData.waterDepth = 1;
            }

            columnData.surfaceBlock = getRiverbedBlockState(worldX, worldZ);
            columnData.underBlock = Blocks.DIRT.defaultBlockState();
        }
    }
}