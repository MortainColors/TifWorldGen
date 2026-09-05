package com.example.tifworldgen.generators.underground;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class UndergroundSpaghetti implements CaveDensityProvider {
    private final NormalNoise caveEntrance, spaghetti3d1, spaghetti3d2, spaghetti3dRarity, spaghetti3dThickness;
    private final NormalNoise spaghetti2d, spaghetti2dModulator, spaghetti2dThickness, spaghetti2dElevation;
    private final NormalNoise spaghettiRoughness, spaghettiRoughnessModulator;

    public UndergroundSpaghetti(PositionalRandomFactory fork) {
        this.caveEntrance = NormalNoise.create(fork.fromHashOf("minecraft:cave_entrance"), new NormalNoise.NoiseParameters(-7, 0.4, 0.5, 1.0));
        this.spaghetti3d1 = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_3d_1"), new NormalNoise.NoiseParameters(-7, 1.0));
        this.spaghetti3d2 = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_3d_2"), new NormalNoise.NoiseParameters(-7, 1.0));
        this.spaghetti3dRarity = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_3d_rarity"), new NormalNoise.NoiseParameters(-11, 1.0));
        this.spaghetti3dThickness = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_3d_thickness"), new NormalNoise.NoiseParameters(-8, 1.0));
        this.spaghetti2d = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_2d"), new NormalNoise.NoiseParameters(-7, 1.0));
        this.spaghetti2dModulator = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_2d_modulator"), new NormalNoise.NoiseParameters(-11, 1.0));
        this.spaghetti2dThickness = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_2d_thickness"), new NormalNoise.NoiseParameters(-11, 1.0));
        this.spaghetti2dElevation = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_2d_elevation"), new NormalNoise.NoiseParameters(-8, 1.0));
        this.spaghettiRoughness = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_roughness"), new NormalNoise.NoiseParameters(-5, 1.0));
        this.spaghettiRoughnessModulator = NormalNoise.create(fork.fromHashOf("minecraft:spaghetti_roughness_modulator"), new NormalNoise.NoiseParameters(-8, 1.0));
    }

    @Override
    public double computeDensity(int x, int y, int z, int depth, double simY, double waterProximity) {
        double rough = (-0.05 - 0.05 * spaghettiRoughnessModulator.getValue(x, y, z))
                * (-0.4 + Math.abs(spaghettiRoughness.getValue(x, y, z)));

        double rarity3 = rarity3D(spaghetti3dRarity.getValue(x * 2.0, y, z * 2.0));
        double s3d1 = rarity3 * Math.abs(spaghetti3d1.getValue(x / rarity3, y / rarity3, z / rarity3));
        double s3d2 = rarity3 * Math.abs(spaghetti3d2.getValue(x / rarity3, y / rarity3, z / rarity3));
        double spag3d = Mth.clamp(Math.max(s3d1, s3d2) + (-0.0765 - 0.0115 * spaghetti3dThickness.getValue(x, y, z)), -1.0, 1.0);

        double entranceGrad = Mth.clampedMap(depth, 40.0, 80.0, 0.0, 0.3);
        double entranceBlend = 0.37 + caveEntrance.getValue(x * 0.75, y * 0.5, z * 0.75) + entranceGrad + 0.3 * waterProximity;
        double entrances = Math.min(entranceBlend, rough + spag3d);

        double thicknessMod = -0.95 - 0.35 * spaghetti2dThickness.getValue(x * 2.0, y, z * 2.0);
        double rarity2 = rarity2D(spaghetti2dModulator.getValue(x * 2.0, y, z * 2.0));
        double wss2d = rarity2 * Math.abs(spaghetti2d.getValue(x / rarity2, y / rarity2, z / rarity2));
        double a = wss2d + 0.083 * thicknessMod;

        double elevN = 8.0 * spaghetti2dElevation.getValue(x, 0.0, z);
        double elev = Math.abs(elevN + Mth.clampedMap(simY, -64.0, 320.0, 8.0, -40.0));
        double spag2dVal = Mth.clamp(Math.max(a, cube(elev + thicknessMod)), -1.0, 1.0);

        return Math.min(entrances, spag2dVal + rough);
    }

    private static double rarity3D(double v) { if (v < -0.5) return 0.75; if (v < 0.0) return 1.0; if (v < 0.5) return 1.5; return 2.0; }
    private static double rarity2D(double v) { if (v < -0.75) return 0.5; if (v < -0.5) return 0.75; if (v < 0.5) return 1.0; if (v < 0.75) return 2.0; return 3.0; }
    private static double cube(double v) { return v * v * v; }
}