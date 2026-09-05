package com.example.tifworldgen.generators;

import com.example.tifworldgen.TifChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class UndergroundBiomeLayerGenerator {

    private static final int SURFACE_BUFFER = 14;

    private NormalNoise lushCaveNoise;
    private NormalNoise deepDarkNoise;
    private NormalNoise deepDarkRegionNoise;
    private NormalNoise dripstoneNoise;

    private volatile boolean initialized = false;

    public UndergroundBiomeLayerGenerator() {}

    public synchronized void ensureInitialized(long seed) {
        if (initialized) return;

        PositionalRandomFactory fork = new XoroshiroRandomSource(seed).forkPositional();
        this.lushCaveNoise       = NormalNoise.create(fork.fromHashOf("tifworldgen:lush_cave"), new NormalNoise.NoiseParameters(-2, 1.0, 0.5));
        this.deepDarkNoise       = NormalNoise.create(fork.fromHashOf("tifworldgen:deep_dark"), new NormalNoise.NoiseParameters(-4, 1.0, 0.5));
        this.deepDarkRegionNoise = NormalNoise.create(fork.fromHashOf("tifworldgen:deep_dark_region"), new NormalNoise.NoiseParameters(-5, 1.0));
        this.dripstoneNoise      = NormalNoise.create(fork.fromHashOf("tifworldgen:dripstone"), new NormalNoise.NoiseParameters(-3, 1.0, 0.5));

        this.initialized = true;
    }


    public ResourceKey<Biome> getUndergroundBiomeKey(int wx, int wy, int wz, int surfaceY, long seed) {
        ensureInitialized(seed);

        int depthBelowSurface = surfaceY - wy;
        if (depthBelowSurface < SURFACE_BUFFER) {
            return null;
        }

        if (isDeepDarkBlob(wx, wy, wz, surfaceY)) {
            return Biomes.DEEP_DARK;
        }

        if (wy < 40 && isLushCaveBlob(wx, wy, wz)) {
            return Biomes.LUSH_CAVES;
        }

        if (wy < 50 && isDripstoneCaveBlob(wx, wy, wz)) {
            return Biomes.DRIPSTONE_CAVES;
        }

        return null;
    }

    public boolean isLushCaveBlob(int wx, int wy, int wz) {
        return initialized && lushCaveNoise.getValue(wx * 0.08, wy * 0.12, wz * 0.08) > 0.42;
    }

    public boolean isDeepDarkBlob(int wx, int wy, int wz, int surfaceY) {
        if (!initialized) return false;

        if (wy > -16 || wy < -58) return false;

        if ((surfaceY - wy) < 70) return false;

        double regionValue = deepDarkRegionNoise.getValue(wx * 0.003, 0.0, wz * 0.003);
        if (regionValue < 0.38) return false;

        double localValue = deepDarkNoise.getValue(wx * 0.015, wy * 0.02, wz * 0.015);
        return localValue > 0.52;
    }

    public boolean isDripstoneCaveBlob(int wx, int wy, int wz) {
        return initialized && dripstoneNoise.getValue(wx * 0.02, wy * 0.03, wz * 0.02) > 0.40;
    }


    public void decorateUndergroundBiomes(ChunkAccess chunk, TifChunkGenerator generator, int minY, long seed) {
        ensureInitialized(seed);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        applySculkDecorations(chunk, generator, minY, seed, pos);
        applyLushCaveDecorations(chunk, minY, seed, pos);
        applyDripstoneCaveDecorations(chunk, minY, seed, pos);
    }

    private void applySculkDecorations(ChunkAccess chunk, TifChunkGenerator generator, int minY, long seed, BlockPos.MutableBlockPos pos) {
        var sculk = Blocks.SCULK.defaultBlockState();
        var sculkSensor = Blocks.SCULK_SENSOR.defaultBlockState();
        var sculkShrieker = Blocks.SCULK_SHRIEKER.defaultBlockState();

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;
                int surfaceY = generator.getColumnData(worldX, worldZ).finalHeight;

                for (int y = minY + 6; y < -16; y++) {
                    if (!isDeepDarkBlob(worldX, y, worldZ, surfaceY)) continue;

                    pos.set(x, y, z);
                    var currentBlock = chunk.getBlockState(pos);

                    if (isSolidBaseBlock(currentBlock)) {
                        boolean exposed = isExposedToCaveAir(chunk, x, y, z, pos);

                        if (exposed) {
                            long blockHash = Mth.getSeed(worldX, y, worldZ) ^ seed;
                            int randVal = Math.abs((int) (blockHash % 100));

                            pos.set(x, y, z);
                            if (randVal < 2) chunk.setBlockState(pos, sculkShrieker, false);
                            else if (randVal < 8) chunk.setBlockState(pos, sculkSensor, false);
                            else chunk.setBlockState(pos, sculk, false);
                        }
                    }
                }
            }
        }
    }

    private void applyLushCaveDecorations(ChunkAccess chunk, int minY, long seed, BlockPos.MutableBlockPos pos) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;

                for (int y = minY + 6; y < 40; y++) {
                    if (!isLushCaveBlob(worldX, y, worldZ)) continue;

                    pos.set(x, y, z);
                    BlockState current = chunk.getBlockState(pos);

                    if (current.is(Blocks.CAVE_AIR)) {
                        BlockPos downPos = pos.below();
                        BlockPos upPos = pos.above();
                        BlockState downState = chunk.getBlockState(downPos);
                        BlockState upState = chunk.getBlockState(upPos);

                        long blockHash = Mth.getSeed(worldX, y, worldZ) ^ seed;
                        int rand = Math.abs((int) (blockHash % 100));

                        if (isSolidBaseBlock(downState)) {
                            if (rand < 25) {
                                chunk.setBlockState(downPos, Blocks.CLAY.defaultBlockState(), false);
                            } else {
                                chunk.setBlockState(downPos, Blocks.MOSS_BLOCK.defaultBlockState(), false);
                                if (rand < 50) chunk.setBlockState(pos, Blocks.MOSS_CARPET.defaultBlockState(), false);
                                else if (rand < 60) chunk.setBlockState(pos, Blocks.AZALEA.defaultBlockState(), false);
                                else if (rand < 68) chunk.setBlockState(pos, Blocks.FLOWERING_AZALEA.defaultBlockState(), false);
                            }
                        }

                        if (isSolidBaseBlock(upState)) {
                            if (rand < 35) chunk.setBlockState(upPos, Blocks.ROOTED_DIRT.defaultBlockState(), false);
                            else chunk.setBlockState(upPos, Blocks.MOSS_BLOCK.defaultBlockState(), false);

                            if (rand < 12) {
                                chunk.setBlockState(pos, Blocks.SPORE_BLOSSOM.defaultBlockState(), false);
                            } else if (rand < 50) {
                                int vineLength = 2 + (rand % 5);
                                for (int v = 0; v < vineLength; v++) {
                                    BlockPos vinePos = pos.below(v);
                                    if (chunk.getBlockState(vinePos).is(Blocks.CAVE_AIR)) {
                                        boolean isTip = (v == vineLength - 1) || !chunk.getBlockState(vinePos.below()).is(Blocks.CAVE_AIR);
                                        BlockState vineState = isTip ? Blocks.CAVE_VINES.defaultBlockState() : Blocks.CAVE_VINES_PLANT.defaultBlockState();
                                        if ((blockHash + v) % 4 == 0) vineState = vineState.setValue(CaveVines.BERRIES, true);
                                        chunk.setBlockState(vinePos, vineState, false);
                                        if (isTip) break;
                                    } else break;
                                }
                            } else if (rand < 75) {
                                chunk.setBlockState(pos, Blocks.HANGING_ROOTS.defaultBlockState(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyDripstoneCaveDecorations(ChunkAccess chunk, int minY, long seed, BlockPos.MutableBlockPos pos) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;

                for (int y = minY + 6; y < 50; y++) {
                    if (!isDripstoneCaveBlob(worldX, y, worldZ)) continue;

                    pos.set(x, y, z);
                    BlockState current = chunk.getBlockState(pos);

                    if (current.is(Blocks.CAVE_AIR)) {
                        BlockPos downPos = pos.below();
                        BlockPos upPos = pos.above();
                        BlockState downState = chunk.getBlockState(downPos);
                        BlockState upState = chunk.getBlockState(upPos);

                        long blockHash = Mth.getSeed(worldX, y, worldZ) ^ seed;
                        int rand = Math.abs((int) (blockHash % 100));

                        if (isSolidBaseBlock(downState) && rand < 60) {
                            chunk.setBlockState(downPos, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), false);
                            if (rand < 25) {
                                chunk.setBlockState(pos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, net.minecraft.core.Direction.UP)
                                        .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP), false);
                            }
                        }

                        if (isSolidBaseBlock(upState) && rand < 60) {
                            chunk.setBlockState(upPos, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), false);
                            if (rand < 30) {
                                chunk.setBlockState(pos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, net.minecraft.core.Direction.DOWN)
                                        .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP), false);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSolidBaseBlock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF);
    }

    private boolean isExposedToCaveAir(ChunkAccess chunk, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        if (x < 15 && chunk.getBlockState(pos.set(x + 1, y, z)).is(Blocks.CAVE_AIR)) return true;
        if (x > 0  && chunk.getBlockState(pos.set(x - 1, y, z)).is(Blocks.CAVE_AIR)) return true;
        if (z < 15 && chunk.getBlockState(pos.set(x, y, z + 1)).is(Blocks.CAVE_AIR)) return true;
        if (z > 0  && chunk.getBlockState(pos.set(x, y, z - 1)).is(Blocks.CAVE_AIR)) return true;
        if (y < chunk.getMaxBuildHeight() - 1 && chunk.getBlockState(pos.set(x, y + 1, z)).is(Blocks.CAVE_AIR)) return true;
        if (y > chunk.getMinBuildHeight() && chunk.getBlockState(pos.set(x, y - 1, z)).is(Blocks.CAVE_AIR)) return true;

        return false;
    }
}