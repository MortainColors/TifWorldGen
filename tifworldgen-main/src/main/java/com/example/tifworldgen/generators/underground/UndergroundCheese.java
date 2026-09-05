package com.example.tifworldgen.generators.underground;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class UndergroundCheese implements CaveDensityProvider {
    private static final double CHEESE_OFFSET = 0.27;
    private final NormalNoise caveLayer;
    private final NormalNoise caveCheese;

    public UndergroundCheese(PositionalRandomFactory fork) {
        this.caveLayer = NormalNoise.create(fork.fromHashOf("minecraft:cave_layer"), new NormalNoise.NoiseParameters(-8, 1.0));
        this.caveCheese = NormalNoise.create(fork.fromHashOf("minecraft:cave_cheese"), new NormalNoise.NoiseParameters(-8, 0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0));
    }

    @Override
    public double computeDensity(int x, int y, int z, int depth, double simY, double waterProximity) {
        double layer = 4.0 * Math.pow(caveLayer.getValue(x, y * 8.0, z), 2);
        double sloped = Mth.clampedMap(depth, 0.0, 24.0, 1.0, 2.5);
        double cheese = Mth.clamp(CHEESE_OFFSET + caveCheese.getValue(x, y * 0.6666666666666666, z), -1.0, 1.0)
                + Mth.clamp(1.5 - 0.64 * sloped, 0.0, 0.5);
        return layer + cheese;
    }
}