package com.example.tifworldgen;

import com.example.tifworldgen.layers.utils.LayerSecurity;
import com.example.tifworldgen.generators.ColumnData;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.seibel.distanthorizons.api.DhApi;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundDHPreGenerator {

    private static int currentRadiusChunks = 64;
    private static int genAboveYLevel = -64;
    private static final AtomicBoolean isLoopRunning = new AtomicBoolean(false);
    private static volatile ServerPlayer activePlayer = null;

    private static final Map<Long, Integer> processedChunksRegistry = new ConcurrentHashMap<>();
    private static File activeWorldRegistryFile = null;

    private static final List<int[]> validTilesToProcess = new ArrayList<>();
    private static int totalTilesInGrid = 0;

    private static short[][] tileFinalHeight = null;
    private static short[][] tileWaterLevel = null;
    private static boolean[][] tileIsWater = null;
    private static byte[][] tileSurfaceType = null;
    private static byte[][] tileUnderType = null;
    private static byte[][] tileForestType = null;
    private static int tileMinBlockX, tileMinBlockZ;

    private static void loadRegistryFromDisk(ServerLevel level) {
        processedChunksRegistry.clear();
        File worldFolder = level.getServer().getWorldPath(LevelResource.ROOT).toFile();
        activeWorldRegistryFile = new File(worldFolder, "dh_scanned_chunks.txt");

        if (!activeWorldRegistryFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(activeWorldRegistryFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.contains(";")) {
                    String[] parts = line.split(";");
                    long key = Long.parseLong(parts[0]);
                    int yBound = Integer.parseInt(parts[1]);
                    processedChunksRegistry.put(key, yBound);
                } else {
                    long key = Long.parseLong(line);
                    processedChunksRegistry.put(key, -64);
                }
            }
            System.out.println("[TifWorldGen-DHPREGEN] Loaded " + processedChunksRegistry.size() + " smart chunk histories.");
        } catch (Exception e) {
            System.err.println("[TifWorldGen-DHPREGEN] Failed to read world chunk history: " + e.getMessage());
        }
    }

    private static synchronized void batchSaveChunksToDisk(List<Long> chunkKeys, int yLevel) {
        if (activeWorldRegistryFile == null || chunkKeys.isEmpty()) return;
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(activeWorldRegistryFile, true)))) {
            for (long key : chunkKeys) {
                writer.println(key + ";" + yLevel);
            }
        } catch (IOException e) {
            System.err.println("[TifWorldGen-DHPREGEN] Could not save chunk batch to world folder: " + e.getMessage());
        }
    }

    private static void prepareAndAnalyzeGrid(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        loadRegistryFromDisk(level);

        if (!(level.getChunkSource().getGenerator() instanceof TifChunkGenerator tiffGenerator)) {
            player.sendSystemMessage(Component.literal("§c[TifWorldGen-DHPREGEN] Active world generator is not TifChunkGenerator!"), false);
            return;
        }

        validTilesToProcess.clear();
        totalTilesInGrid = 0;

        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        int TILE_SIZE = 32;

        int startCX = centerChunkX - currentRadiusChunks;
        int endCX = centerChunkX + currentRadiusChunks - 1;
        int startCZ = centerChunkZ - currentRadiusChunks;
        int endCZ = centerChunkZ + currentRadiusChunks - 1;

        player.sendSystemMessage(Component.literal("§e[TifWorldGen-DHPREGEN] Analyzing TIFF heightmaps for Y >= " + genAboveYLevel + "..."), false);

        for (int tileX = startCX; tileX <= endCX; tileX += TILE_SIZE) {
            for (int tileZ = startCZ; tileZ <= endCZ; tileZ += TILE_SIZE) {
                totalTilesInGrid++;

                int tStartCX = tileX;
                int tEndCX = tileX + TILE_SIZE - 1;
                int tStartCZ = tileZ;
                int tEndCZ = tileZ + TILE_SIZE - 1;

                boolean tileNeedsWork = false;
                for (int cx = tStartCX; cx <= tEndCX; cx++) {
                    for (int cz = tStartCZ; cz <= tEndCZ; cz++) {
                        long k = ChunkPos.asLong(cx, cz);
                        if (!processedChunksRegistry.containsKey(k) || processedChunksRegistry.get(k) > genAboveYLevel) {
                            tileNeedsWork = true;
                            break;
                        }
                    }
                    if (tileNeedsWork) break;
                }

                if (!tileNeedsWork) continue;

                int minBX = tStartCX * 16;
                int maxBX = tEndCX * 16 + 15;
                int minBZ = tStartCZ * 16;
                int maxBZ = tEndCZ * 16 + 15;
                int sizeX = (maxBX - minBX) + 1;
                int sizeZ = (maxBZ - minBZ) + 1;

                boolean tileHasAnyValidTerrain = false;
                int scanStep = 8;

                scanLoop:
                for (int bx = 0; bx < sizeX; bx += scanStep) {
                    for (int bz = 0; bz < sizeZ; bz += scanStep) {
                        int wX = minBX + bx;
                        int wZ = minBZ + bz;
                        ColumnData col = tiffGenerator.getColumnData(wX, wZ);
                        int highestPoint = col.finalHeight;
                        if (col.isWater) {
                            highestPoint = Math.max(highestPoint, col.waterLevel);
                        }
                        if (highestPoint >= genAboveYLevel) {
                            tileHasAnyValidTerrain = true;
                            break scanLoop;
                        }
                    }
                }

                if (tileHasAnyValidTerrain) {
                    validTilesToProcess.add(new int[]{tileX, tileZ});
                } else {
                    List<Long> emptyTileChunks = new ArrayList<>();
                    for (int cx = tStartCX; cx <= tEndCX; cx++) {
                        for (int cz = tStartCZ; cz <= tEndCZ; cz++) {
                            long chunkKey = ChunkPos.asLong(cx, cz);
                            if (!processedChunksRegistry.containsKey(chunkKey) || processedChunksRegistry.get(chunkKey) > genAboveYLevel) {
                                emptyTileChunks.add(chunkKey);
                            }
                        }
                    }
                    if (!emptyTileChunks.isEmpty()) {
                        synchronized (processedChunksRegistry) {
                            for (long key : emptyTileChunks) {
                                processedChunksRegistry.put(key, genAboveYLevel);
                            }
                        }
                        batchSaveChunksToDisk(emptyTileChunks, genAboveYLevel);
                    }
                }
            }
        }

        int skippedTiles = totalTilesInGrid - validTilesToProcess.size();
        player.sendSystemMessage(Component.literal("§a§l[TifWorldGen-DHPREGEN] History mapped & Analysis complete!"), false);
        player.sendSystemMessage(Component.literal("§bTotal Tiles in Grid: §e" + totalTilesInGrid), false);
        player.sendSystemMessage(Component.literal("§bActive Tiles to Generate: §a" + validTilesToProcess.size()), false);
        player.sendSystemMessage(Component.literal("§bEmpty Tiles Skipped: §7" + skippedTiles), false);
    }

    private static void startPregenLoop(ServerPlayer player) {
        if (!LayerSecurity.isAuthorizedCaller()) {
            player.sendSystemMessage(Component.literal("§c[TifWorldGen-DHPREGEN] Access Denied!"), false);
            return;
        }

        if (validTilesToProcess.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[TifWorldGen-DHPREGEN] No active tiles to generate! Run 'prepare' command first or adjust Y level."), false);
            return;
        }

        if (!isLoopRunning.compareAndSet(false, true)) {
            player.sendSystemMessage(Component.literal("§c[TifWorldGen-DHPREGEN] Pregeneration is already running! Use 'stop' first."), false);
            return;
        }

        activePlayer = player;
        ServerLevel level = player.serverLevel();
        net.minecraft.server.MinecraftServer server = level.getServer();

        player.sendSystemMessage(Component.literal("§e[TifWorldGen-DHPREGEN] Multi-Threaded Tiling Simulation started using pre-scanned map..."), false);

        Thread masterOrchestratorThread = new Thread(() -> {
            if (!(level.getChunkSource().getGenerator() instanceof TifChunkGenerator tiffGenerator)) {
                isLoopRunning.set(false);
                return;
            }

            int TILE_SIZE = 32;
            int totalChunksToProcess = totalTilesInGrid * TILE_SIZE * TILE_SIZE;
            int processedChunks = (totalTilesInGrid - validTilesToProcess.size()) * TILE_SIZE * TILE_SIZE;
            int nextReportPercent = 5;

            int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            ExecutorService chunkExecutor = Executors.newFixedThreadPool(cores, new java.util.concurrent.ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "WMap-DH-Worker-" + counter.getAndIncrement());
                }
            });

            int activeTileCounter = 0;
            int totalActiveTiles = validTilesToProcess.size();

            for (int[] tileCoords : validTilesToProcess) {
                if (!isLoopRunning.get()) {
                    chunkExecutor.shutdownNow();
                    clearTileCache();
                    isLoopRunning.set(false);
                    return;
                }

                activeTileCounter++;
                int tileX = tileCoords[0];
                int tileZ = tileCoords[1];

                int tStartCX = tileX;
                int tEndCX = tileX + TILE_SIZE - 1;
                int tStartCZ = tileZ;
                int tEndCZ = tileZ + TILE_SIZE - 1;

                final int displayActiveIdx = activeTileCounter;
                final int displayTotalActive = totalActiveTiles;
                server.execute(() -> {
                    if (activePlayer != null) {
                        activePlayer.sendSystemMessage(Component.literal("§d§l[TifWorldGen-DHPREGEN] RAM Cache: Loading Active Tile [" + displayActiveIdx + "/" + displayTotalActive + "]..."), false);
                    }
                });

                int minBX = tStartCX * 16;
                int maxBX = tEndCX * 16 + 15;
                int minBZ = tStartCZ * 16;
                int maxBZ = tEndCZ * 16 + 15;
                int sizeX = (maxBX - minBX) + 1;
                int sizeZ = (maxBZ - minBZ) + 1;

                tileMinBlockX = minBX;
                tileMinBlockZ = minBZ;

                tileFinalHeight = new short[sizeX][sizeZ];
                tileWaterLevel = new short[sizeX][sizeZ];
                tileIsWater = new boolean[sizeX][sizeZ];
                tileSurfaceType = new byte[sizeX][sizeZ];
                tileUnderType = new byte[sizeX][sizeZ];
                tileForestType = new byte[sizeX][sizeZ];

                for (int bx = 0; bx < sizeX; bx++) {
                    for (int bz = 0; bz < sizeZ; bz++) {
                        int wX = minBX + bx;
                        int wZ = minBZ + bz;
                        ColumnData col = tiffGenerator.getColumnData(wX, wZ);
                        tileFinalHeight[bx][bz] = (short) col.finalHeight;
                        tileWaterLevel[bx][bz] = (short) col.waterLevel;
                        tileIsWater[bx][bz] = col.isWater;
                        tileSurfaceType[bx][bz] = getBlockTypeByte(col.surfaceBlock);
                        tileUnderType[bx][bz] = getBlockTypeByte(col.underBlock);
                        tileForestType[bx][bz] = tiffGenerator.getForestLayer() != null ? (byte) tiffGenerator.getForestLayer().getForestType(wX, wZ) : 0;
                    }
                }

                List<Future<?>> futures = new ArrayList<>();
                List<Long> newlyProcessedInTile = new ArrayList<>();

                final int finalSizeX = sizeX;
                final int finalSizeZ = sizeZ;

                for (int cx = tStartCX; cx <= tEndCX; cx++) {
                    for (int cz = tStartCZ; cz <= tEndCZ; cz++) {
                        long chunkKey = ChunkPos.asLong(cx, cz);

                        if (processedChunksRegistry.containsKey(chunkKey) && processedChunksRegistry.get(chunkKey) <= genAboveYLevel) {
                            processedChunks++;
                            continue;
                        }

                        final int finalCx = cx;
                        final int finalCz = cz;
                        final long finalChunkKey = chunkKey;

                        futures.add(chunkExecutor.submit(() -> {
                            try {
                                boolean chunkHasTerrainAboveY = false;
                                int chStartBX = finalCx * 16;
                                int chStartBZ = finalCz * 16;

                                for (int x = 0; x < 16; x++) {
                                    for (int z = 0; z < 16; z++) {
                                        int cacheX = (chStartBX + x) - tileMinBlockX;
                                        int cacheZ = (chStartBZ + z) - tileMinBlockZ;
                                        if (cacheX >= 0 && cacheX < finalSizeX && cacheZ >= 0 && cacheZ < finalSizeZ) {
                                            int highestPoint = tileFinalHeight[cacheX][cacheZ];
                                            if (tileIsWater[cacheX][cacheZ]) {
                                                highestPoint = Math.max(highestPoint, tileWaterLevel[cacheX][cacheZ]);
                                            }
                                            if (highestPoint >= genAboveYLevel) {
                                                chunkHasTerrainAboveY = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (chunkHasTerrainAboveY) break;
                                }

                                if (chunkHasTerrainAboveY) {
                                    injectSurfaceToDistantHorizons(level, finalCx, finalCz, tiffGenerator.getMinY());
                                }

                                synchronized (newlyProcessedInTile) {
                                    newlyProcessedInTile.add(finalChunkKey);
                                }
                            } catch (Exception e) {
                                System.err.println("[TifWorldGen-DHPREGEN] Thread injection error at [" + finalCx + "," + finalCz + "]: " + e.getMessage());
                            }
                        }));
                    }
                }

                for (Future<?> future : futures) {
                    try { future.get(); } catch (Exception e) { e.printStackTrace(); }
                }

                if (!newlyProcessedInTile.isEmpty()) {
                    synchronized (processedChunksRegistry) {
                        for (long key : newlyProcessedInTile) {
                            processedChunksRegistry.put(key, genAboveYLevel);
                        }
                    }
                    batchSaveChunksToDisk(newlyProcessedInTile, genAboveYLevel);
                    processedChunks += newlyProcessedInTile.size();
                }

                clearTileCache();

                int percent = (int) ((processedChunks * 100.0f) / totalChunksToProcess);
                if (percent >= nextReportPercent) {
                    reportProgress(server, percent, processedChunks, totalChunksToProcess);
                    nextReportPercent = ((percent / 5) * 5) + 5;
                }
            }

            chunkExecutor.shutdown();
            clearTileCache();
            isLoopRunning.set(false);

            server.execute(() -> {
                if (activePlayer != null) {
                    activePlayer.sendSystemMessage(Component.literal("§a§l[TifWorldGen-DHPREGEN] Pregeneration successfully completed! All active tiles processed."), false);
                }
            });
        });
        masterOrchestratorThread.setName("WMap-DH-Orchestrator");
        masterOrchestratorThread.start();
    }

    private static void reportProgress(net.minecraft.server.MinecraftServer server, int percent, int processed, int total) {
        server.execute(() -> {
            if (activePlayer != null && activePlayer.connection != null) {
                activePlayer.sendSystemMessage(Component.literal("§b[TifWorldGen-DHPREGEN] Progress: " + percent + "% (" + processed + "/" + total + ")"), false);
            }
        });
        System.out.println("[TifWorldGen-DHPREGEN] Progress: " + percent + "%");
    }

    private static void injectSurfaceToDistantHorizons(ServerLevel level, int chunkX, int chunkZ, int minY) {
        if (DhApi.Delayed.terrainRepo == null || DhApi.Delayed.worldProxy == null) return;

        String dimensionName = level.dimension().location().toString();
        com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper levelWrapper = null;
        for (com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper wrapper :
                DhApi.Delayed.worldProxy.getAllLoadedLevelsWithDimensionNameLike(dimensionName)) {
            levelWrapper = wrapper;
            break;
        }
        if (levelWrapper == null) {
            try { levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel(); } catch (IllegalStateException e) { return; }
        }

        net.minecraft.world.level.chunk.LevelChunk simulatedChunk =
                com.example.tifworldgen.DhPregenChunk.buildSimulatedChunk(
                        level, chunkX, chunkZ, minY,
                        tileFinalHeight, tileWaterLevel, tileIsWater,
                        tileSurfaceType, tileUnderType, tileForestType,
                        tileMinBlockX, tileMinBlockZ
                );

        Object[] dataPackage = new Object[] { simulatedChunk, level };
        try {
            DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(levelWrapper, dataPackage);
        } catch (Exception e) {
            System.err.println("[TifWorldGen-DHPREGEN] DH API data injection failed: " + e.getMessage());
        }
    }

    private static byte getBlockTypeByte(BlockState state) {
        if (state == Blocks.GRASS_BLOCK.defaultBlockState()) return 0;
        if (state == Blocks.DIRT.defaultBlockState()) return 1;
        if (state == Blocks.SAND.defaultBlockState()) return 2;
        if (state == Blocks.GRAVEL.defaultBlockState()) return 3;
        if (state == Blocks.CLAY.defaultBlockState()) return 4;
        return 5;
    }

    private static void clearTileCache() {
        tileFinalHeight = null; tileWaterLevel = null; tileIsWater = null;
        tileSurfaceType = null; tileUnderType = null; tileForestType = null;
        System.gc();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tifworldgen").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("dhpregen")
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 4000))
                                                .executes(c -> {
                                                    int inputRadius = IntegerArgumentType.getInteger(c, "radius");
                                                    int alignedRadius = ((inputRadius + 15) / 16) * 16;
                                                    currentRadiusChunks = alignedRadius;
                                                    c.getSource().sendSuccess(() -> Component.literal("§a[TifWorldGen-DHPREGEN] Radius locked to exactly " + alignedRadius + " chunks."), false);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("genaboveylevel")
                                        .then(Commands.argument("ylevel", IntegerArgumentType.integer(-64, 2000))
                                                .executes(c -> {
                                                    genAboveYLevel = IntegerArgumentType.getInteger(c, "ylevel");
                                                    c.getSource().sendSuccess(() -> Component.literal("§a[TifWorldGen-DHPREGEN] Height filter active! Layer: Y >= " + genAboveYLevel), false);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("prepare").executes(c -> {
                                    ServerPlayer preparePlayer = c.getSource().getPlayerOrException();
                                    Thread prepareThread = new Thread(() -> prepareAndAnalyzeGrid(preparePlayer), "WMap-DH-Prepare");
                                    prepareThread.start();
                                    c.getSource().sendSuccess(() -> Component.literal("§e[TifWorldGen-DHPREGEN] Tile analysis started in background, watch chat for results..."), false);
                                    return 1;
                                }))
                                .then(Commands.literal("start").executes(c -> {
                                    startPregenLoop(c.getSource().getPlayerOrException());
                                    return 1;
                                }))
                                .then(Commands.literal("stop").executes(c -> {
                                    isLoopRunning.set(false);
                                    c.getSource().sendSuccess(() -> Component.literal("§c[TifWorldGen-DHPREGEN] Stopping after current tile... (cache is cleaned up by the orchestrator)"), false);
                                    return 1;
                                }))
                        )
        );
    }
}