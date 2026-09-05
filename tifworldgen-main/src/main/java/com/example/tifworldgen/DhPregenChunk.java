
package com.example.tifworldgen;

import com.example.tifworldgen.TifChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.biome.Biome;

public class DhPregenChunk {

    public static LevelChunk buildSimulatedChunk(
            ServerLevel level, int chunkX, int chunkZ, int minY,
            short[][] finalHeightCache, short[][] waterLevelCache, boolean[][] isWaterCache,
            byte[][] surfaceTypeCache, byte[][] underTypeCache, byte[][] forestTypeCache,
            int minBlockX, int minBlockZ
    ) {
        LevelChunk memoryChunk = new LevelChunk(level, new ChunkPos(chunkX, chunkZ));

        int startBlockX = chunkX * 16;
        int startBlockZ = chunkZ * 16;

        int sizeX = finalHeightCache.length;
        int sizeZ = finalHeightCache[0].length;

        try {
            if (level.getChunkSource().getGenerator() instanceof TifChunkGenerator tiffGen) {
                tiffGen.createBiomes(Runnable::run, level.getChunkSource().randomState(), Blender.empty(), level.structureManager(), memoryChunk);
            }
        } catch (Exception ignored) {}

        LevelChunkSection[] sections = memoryChunk.getSections();
        int minSectionIndex = memoryChunk.getMinSection();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startBlockX + x;
                int worldZ = startBlockZ + z;
                int cacheX = worldX - minBlockX;
                int cacheZ = worldZ - minBlockZ;

                if (cacheX < 0 || cacheX >= sizeX || cacheZ < 0 || cacheZ >= sizeZ) continue;

                int terrainH = finalHeightCache[cacheX][cacheZ];
                BlockState surfaceState = getBlockStateFromByte(surfaceTypeCache[cacheX][cacheZ]);
                BlockState underState = getBlockStateFromByte(underTypeCache[cacheX][cacheZ]);
                boolean hasWater = isWaterCache[cacheX][cacheZ];
                int wLevel = waterLevelCache[cacheX][cacheZ];

                setBlockFast(sections, minSectionIndex, worldX, terrainH, worldZ, surfaceState);

                for (int y = terrainH - 1; y >= terrainH - 3; y--) {
                    setBlockFast(sections, minSectionIndex, worldX, y, worldZ, underState);
                }
                for (int y = terrainH - 4; y >= terrainH - 5; y--) {
                    setBlockFast(sections, minSectionIndex, worldX, y, worldZ, Blocks.STONE.defaultBlockState());
                }

                int topY = hasWater ? wLevel : terrainH;
                boolean isColdAndHigh = false;

                if (topY >= 1300) {
                    Holder<Biome> biomeHolder = memoryChunk.getNoiseBiome(worldX >> 2, topY >> 2, worldZ >> 2);
                    if (biomeHolder != null) {
                        isColdAndHigh = biomeHolder.value().coldEnoughToSnow(new BlockPos(worldX, topY, worldZ));
                    }
                }

                if (hasWater) {
                    int waterTop = Math.max(wLevel, terrainH);
                    setBlockFast(sections, minSectionIndex, worldX, terrainH - 1, worldZ, surfaceState);
                    for (int y = terrainH; y <= waterTop; y++) {
                        if (y == waterTop && isColdAndHigh) {
                            setBlockFast(sections, minSectionIndex, worldX, y, worldZ, Blocks.ICE.defaultBlockState());
                        } else {
                            setBlockFast(sections, minSectionIndex, worldX, y, worldZ, Blocks.WATER.defaultBlockState());
                        }
                    }
                    if (isColdAndHigh) {
                        setBlockFast(sections, minSectionIndex, worldX, waterTop + 1, worldZ, Blocks.SNOW.defaultBlockState());
                    }
                } else {
                    if (isColdAndHigh) {
                        setBlockFast(sections, minSectionIndex, worldX, terrainH + 1, worldZ, Blocks.SNOW.defaultBlockState());
                    }

                    int fType = forestTypeCache[cacheX][cacheZ];
                    if (fType > 0) {
                        long seed = ((long) worldX * 341873128712L + (long) worldZ * 132897987541L) ^ 54918291823912L;
                        seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                        float randFloat = (float) (seed >>> 24) / 16777216.0f;

                        if (randFloat < 0.12f) {
                            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                            int trunkMod = (int) ((seed >>> 24) % 3);

                            if (fType == 2) {
                                int trunkHeight = 6 + Math.abs(trunkMod);
                                for (int h = 1; h <= trunkHeight; h++) {
                                    setBlockFast(sections, minSectionIndex, worldX, terrainH + h, worldZ, Blocks.SPRUCE_LOG.defaultBlockState());
                                }
                                drawLeafCircleFast(sections, minSectionIndex, worldX, worldZ, terrainH + trunkHeight - 3, 2, Blocks.SPRUCE_LEAVES.defaultBlockState(), startBlockX, startBlockZ);
                                drawLeafCircleFast(sections, minSectionIndex, worldX, worldZ, terrainH + trunkHeight - 1, 1, Blocks.SPRUCE_LEAVES.defaultBlockState(), startBlockX, startBlockZ);
                                setBlockFast(sections, minSectionIndex, worldX, terrainH + trunkHeight, worldZ, Blocks.SPRUCE_LEAVES.defaultBlockState());
                                setBlockFast(sections, minSectionIndex, worldX, terrainH + trunkHeight + 1, worldZ, Blocks.SPRUCE_LEAVES.defaultBlockState());
                            } else {
                                int trunkHeight = 4 + Math.abs(trunkMod % 2);
                                for (int h = 1; h <= trunkHeight; h++) {
                                    setBlockFast(sections, minSectionIndex, worldX, terrainH + h, worldZ, Blocks.OAK_LOG.defaultBlockState());
                                }
                                for (int ly = -1; ly <= 1; ly++) {
                                    int radius = (ly == 0) ? 2 : 1;
                                    drawLeafCircleFast(sections, minSectionIndex, worldX, worldZ, terrainH + trunkHeight + ly, radius, Blocks.OAK_LEAVES.defaultBlockState(), startBlockX, startBlockZ);
                                }
                                setBlockFast(sections, minSectionIndex, worldX, terrainH + trunkHeight + 2, worldZ, Blocks.OAK_LEAVES.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }

        return memoryChunk;
    }

    private static void setBlockFast(LevelChunkSection[] sections, int minSectionIndex, int worldX, int y, int worldZ, BlockState state) {
        int secIndex = (y >> 4) - minSectionIndex;
        if (secIndex < 0 || secIndex >= sections.length) return;

        LevelChunkSection section = sections[secIndex];
        if (section != null) {
            section.setBlockState(worldX & 15, y & 15, worldZ & 15, state);
        }
    }

    private static void drawLeafCircleFast(LevelChunkSection[] sections, int minSection, int centerX, int centerZ, int y, int radius, BlockState leafState, int startX, int startZ) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 1) continue;

                int targetWorldX = centerX + dx;
                int targetWorldZ = centerZ + dz;

                int localX = targetWorldX - startX;
                int localZ = targetWorldZ - startZ;
                if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                    setBlockFast(sections, minSection, targetWorldX, y, targetWorldZ, leafState);
                }
            }
        }
    }

    private static BlockState getBlockStateFromByte(byte type) {
        switch (type) {
            case 0: return Blocks.GRASS_BLOCK.defaultBlockState();
            case 1: return Blocks.DIRT.defaultBlockState();
            case 2: return Blocks.SAND.defaultBlockState();
            case 3: return Blocks.GRAVEL.defaultBlockState();
            case 4: return Blocks.CLAY.defaultBlockState();
            default: return Blocks.STONE.defaultBlockState();
        }
    }
}