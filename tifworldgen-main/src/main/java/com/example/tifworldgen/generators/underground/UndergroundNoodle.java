package com.example.tifworldgen.generators.underground;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class UndergroundNoodle {
    private final NormalNoise noodle, noodleThickness, noodleRidgeA, noodleRidgeB;

    public UndergroundNoodle(PositionalRandomFactory fork) {
        this.noodle = NormalNoise.create(fork.fromHashOf("minecraft:noodle"), new NormalNoise.NoiseParameters(-8, 1.0));
        this.noodleThickness = NormalNoise.create(fork.fromHashOf("minecraft:noodle_thickness"), new NormalNoise.NoiseParameters(-8, 1.0));
        this.noodleRidgeA = NormalNoise.create(fork.fromHashOf("minecraft:noodle_ridge_a"), new NormalNoise.NoiseParameters(-7, 1.0));
        this.noodleRidgeB = NormalNoise.create(fork.fromHashOf("minecraft:noodle_ridge_b"), new NormalNoise.NoiseParameters(-7, 1.0));
    }

    public boolean hasNoodleCave(int x, int y, int z, double simY) {
        if (simY < -60.0 || simY > 321.0) return false;
        if (noodle.getValue(x, y, z) < 0.0) return false;

        double thick = -0.075 - 0.025 * noodleThickness.getValue(x, y, z);
        double ridgeScale = 8.0 / 3.0;
        double rx = x * ridgeScale;
        double ry = y * ridgeScale;
        double rz = z * ridgeScale;

        double rA = Math.abs(noodleRidgeA.getValue(rx, ry, rz));
        double rB = Math.abs(noodleRidgeB.getValue(rx, ry, rz));

        double noodleDensity = thick + 1.5 * Math.max(rA, rB);

        return noodleDensity < 0.0;
    }
}