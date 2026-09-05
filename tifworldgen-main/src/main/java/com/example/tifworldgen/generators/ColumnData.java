package com.example.tifworldgen.generators;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class ColumnData {
    public int rawHeight;
    public int finalHeight;
    public int waterLevel;
    public boolean isWater;
    public double distanceToWater;
    public int waterDepth;

    public BlockState surfaceBlock;
    public BlockState underBlock;

    public ColumnData(int rawHeight) {
        this.rawHeight = rawHeight;
        this.finalHeight = rawHeight;
        this.waterLevel = rawHeight;
        this.isWater = false;
        this.distanceToWater = Double.MAX_VALUE;

        this.surfaceBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        this.underBlock = Blocks.DIRT.defaultBlockState();
    }
}