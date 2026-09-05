package com.example.tifworldgen.generators.underground;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.Random;

public class UndergroundAquifer {
    private final NormalNoise macroNoise;
    private final NormalNoise microNoise;

    public UndergroundAquifer(PositionalRandomFactory fork) {
        this.macroNoise = NormalNoise.create(fork.fromHashOf("minecraft:offset"), new NormalNoise.NoiseParameters(-3, 1.0, 1.0, 1.0, 0.0));
        this.microNoise = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_2d"), new NormalNoise.NoiseParameters(-1, 1.0));
    }

    public boolean isWaterSource(int x, int y, int z) {
        if (y > 40) return false;

        int gridX = Math.floorDiv(x, 32);
        int gridZ = Math.floorDiv(z, 32);

        long seed = gridX * 341873128712L + gridZ * 132897987541L;
        Random random = new Random(seed);

        if (random.nextDouble() > 0.25) return false;

        int centerX = gridX * 32 + 16 + random.nextInt(16) - 8;
        int centerZ = gridZ * 32 + 16 + random.nextInt(16) - 8;

        int fluidLevel = -20 + random.nextInt(30);
        if (y > fluidLevel) return false;

        double baseRadius = 10.0 + random.nextInt(10);

        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt((dx * dx) + (dz * dz));

        double macroShape = macroNoise.getValue(x * 0.05, y * 0.08, z * 0.05) * 6.0;

        double microShape = microNoise.getValue(x * 0.15, y * 0.2, z * 0.15) * 3.0;

        double dynamicRadius = Math.max(1.0, baseRadius + macroShape + microShape);

        return dist <= dynamicRadius;
    }
}