package com.example.tifworldgen.generators;

import com.example.tifworldgen.TifChunkGenerator;
import com.example.tifworldgen.generators.underground.UndergroundAquifer;
import com.example.tifworldgen.generators.underground.UndergroundCheese;
import com.example.tifworldgen.generators.underground.UndergroundNoodle;
import com.example.tifworldgen.generators.underground.UndergroundSpaghetti;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

import java.util.Random;

public class UndergroundLayerGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SURFACE_BUFFER = 6;

    private static final int CW = 4, CH = 8;

    private UndergroundCheese cheeseGen;
    private UndergroundSpaghetti spaghettiGen;
    private UndergroundNoodle noodleGen;
    private UndergroundAquifer aquiferGen;

    private NormalNoise deepslateNoise;

    private volatile boolean initialized = false;

    public synchronized void ensureInitialized(long seed) {
        if (initialized) return;
        PositionalRandomFactory fork = new XoroshiroRandomSource(seed).forkPositional();

        this.cheeseGen = new UndergroundCheese(fork);
        this.spaghettiGen = new UndergroundSpaghetti(fork);
        this.noodleGen = new UndergroundNoodle(fork);
        this.aquiferGen = new UndergroundAquifer(fork);

        this.deepslateNoise = NormalNoise.create(fork.fromHashOf("minecraft:offset"), new NormalNoise.NoiseParameters(-3, 1.0, 1.0, 1.0, 0.0));

        this.initialized = true;
    }

    public void generate(ChunkAccess chunk, NoiseRouter noiseRouter, TifChunkGenerator generator, long seed, StructureManager structureManager) {
        ensureInitialized(seed);

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minBuildHeight = chunk.getMinBuildHeight();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        var bedrock = Blocks.BEDROCK.defaultBlockState();
        var deepslate = Blocks.DEEPSLATE.defaultBlockState();
        var stone = Blocks.STONE.defaultBlockState();
        var caveAir = Blocks.CAVE_AIR.defaultBlockState();
        var lava = Blocks.LAVA.defaultBlockState();
        var water = Blocks.WATER.defaultBlockState();

        int carvedBlocks = 0;

        int[][] surfaceMap = new int[16][16];
        int maxTop = minBuildHeight;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                surfaceMap[x][z] = generator.getColumnData(minX + x, minZ + z).finalHeight;
                if (surfaceMap[x][z] > maxTop) maxTop = surfaceMap[x][z];
            }
        }
        if (maxTop <= minBuildHeight) return;

        Beardifier beardifier = (structureManager != null) ? Beardifier.forStructuresInChunk(structureManager, chunk.getPos()) : null;

        int cellsY = (maxTop - minBuildHeight) / CH + 1;
        double[][][] grid = new double[5][5][cellsY + 1];

        for (int i = 0; i <= 4; i++) {
            for (int k = 0; k <= 4; k++) {
                int wx = minX + i * CW;
                int wz = minZ + k * CW;
                int surfY = surfaceMap[Math.min(i * CW, 15)][Math.min(k * CW, 15)];

                for (int cy = 0; cy <= cellsY; cy++) {
                    int y = minBuildHeight + cy * CH;
                    if (y > surfY) {
                        grid[i][k][cy] = 1.0;
                        continue;
                    }

                    int depth = surfY - y;
                    double simY = Mth.clamp(y, -64.0, 320.0);

                    double cheeseD = cheeseGen.computeDensity(wx, y, wz, depth, simY, 0.0);
                    double spagD = spaghettiGen.computeDensity(wx, y, wz, depth, simY, 0.0);

                    double beardDensity = 0.0;
                    if (beardifier != null) {
                        beardDensity = beardifier.compute(new DensityFunction.SinglePointContext(wx, y, wz));
                    }

                    grid[i][k][cy] = Math.min(cheeseD, spagD) + beardDensity;
                }
            }
        }

        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                for (int cy = 0; cy < cellsY; cy++) {
                    double c000 = grid[cellX][cellZ][cy],         c100 = grid[cellX + 1][cellZ][cy];
                    double c010 = grid[cellX][cellZ][cy + 1],     c110 = grid[cellX + 1][cellZ][cy + 1];
                    double c001 = grid[cellX][cellZ + 1][cy],     c101 = grid[cellX + 1][cellZ + 1][cy];
                    double c011 = grid[cellX][cellZ + 1][cy + 1], c111 = grid[cellX + 1][cellZ + 1][cy + 1];

                    for (int lx = 0; lx < CW; lx++) {
                        int x = cellX * CW + lx;
                        int wx = minX + x;
                        double tx = lx / (double) CW;

                        for (int lz = 0; lz < CW; lz++) {
                            int z = cellZ * CW + lz;
                            int wz = minZ + z;
                            double tz = lz / (double) CW;

                            int surfaceY = surfaceMap[x][z];
                            int maxCaveY = surfaceY - SURFACE_BUFFER;

                            for (int ly = 0; ly < CH; ly++) {
                                int y = minBuildHeight + cy * CH + ly;
                                if (y > surfaceY) continue;

                                pos.set(wx, y, wz);

                                if (y <= minBuildHeight + 4) {
                                    chunk.setBlockState(pos, bedrock, false);
                                    continue;
                                }

                                if (y < maxCaveY) {
                                    double ty = ly / (double) CH;
                                    double d = Mth.lerp3(tx, ty, tz, c000, c100, c010, c110, c001, c101, c011, c111);

                                    double simY = Mth.clamp(y, -64.0, 320.0);
                                    boolean isNoodle = noodleGen.hasNoodleCave(wx, y, wz, simY);

                                    boolean isCaveSpace = (d < 0.0) || isNoodle;

                                    if (isCaveSpace) {
                                        boolean isWater = aquiferGen.isWaterSource(wx, y, wz);
                                        BlockState finalState = isWater ? water : (y < minBuildHeight + 12 ? lava : caveAir);

                                        if (!isWater && (finalState == caveAir || finalState == lava)) {
                                            boolean touchesWater = false;
                                            if (aquiferGen.isWaterSource(wx - 1, y, wz)) touchesWater = true;
                                            else if (aquiferGen.isWaterSource(wx + 1, y, wz)) touchesWater = true;
                                            else if (aquiferGen.isWaterSource(wx, y, wz - 1)) touchesWater = true;
                                            else if (aquiferGen.isWaterSource(wx, y, wz + 1)) touchesWater = true;
                                            else if (aquiferGen.isWaterSource(wx, y + 1, wz)) touchesWater = true;

                                            if (touchesWater) {
                                                if (finalState == lava) {
                                                    finalState = Blocks.OBSIDIAN.defaultBlockState();
                                                } else {
                                                    finalState = (y < 0) ? deepslate : stone;
                                                }
                                            }
                                        }
                                        chunk.setBlockState(pos, finalState, false);
                                        carvedBlocks++;
                                    } else {
                                        if (y <= -8) {
                                            chunk.setBlockState(pos, deepslate, false);
                                        } else if (y < 16) {
                                            double factor = (16.0 - y) / 24.0;
                                            double blockNoise = deepslateNoise.getValue(wx * 0.35, y * 0.35, wz * 0.35) * 0.30;
                                            long hash = Mth.getSeed(wx, y, wz);
                                            double dither = ((hash & 0xFFFF) / 65535.0 - 0.5) * 0.20;
                                            double sample = factor + blockNoise + dither;
                                            chunk.setBlockState(pos, sample > 0.5 ? deepslate : stone, false);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug("[TIF Underground] Chunk [{}, {}] | Carved blocks: {}", chunk.getPos().x, chunk.getPos().z, carvedBlocks);
    }
}