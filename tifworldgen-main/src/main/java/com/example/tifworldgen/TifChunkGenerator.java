package com.example.tifworldgen;

import com.example.tifworldgen.config.ConfigManager;
import com.example.tifworldgen.generators.*;
import com.example.tifworldgen.layers.ForestLayer;
import com.example.tifworldgen.layers.StructureLayer;
import com.example.tifworldgen.layers.WaterLayer;
import com.example.tifworldgen.layers.utils.HeightMapReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class TifChunkGenerator extends ChunkGenerator {
    private final GeneratorSettings settings;
    private final UndergroundLayerGenerator undergroundGenerator;
    private final UndergroundBiomeLayerGenerator undergroundBiomeGenerator;
    private final BiomeSource rawBiomeSource;

    private volatile HeightMapReader heightMapReader;
    private volatile WaterLayer waterLayer;
    private volatile ForestLayer forestLayer;
    private volatile StructureLayer structureLayer;

    private StructureLayerGenerator structureGenerator;
    private UndergroundStructureLayerGenerator undergroundStructureGenerator;


    private volatile long worldSeed = 0L;

    private final BiomeLayerGenerator biomeLayerGenerator;
    private final List<LayerGenerator> pipeline;

    public TifChunkGenerator(BiomeSource biomeSource, GeneratorSettings settings) {
        this(biomeSource, settings, new UndergroundLayerGenerator(), new UndergroundBiomeLayerGenerator());
    }

    private TifChunkGenerator(BiomeSource biomeSource, GeneratorSettings settings, UndergroundLayerGenerator undergroundGen, UndergroundBiomeLayerGenerator undergroundBiomeGen) {
        this(biomeSource, settings, undergroundGen, undergroundBiomeGen, new UndergroundBiomeSourceWrapper(biomeSource, undergroundBiomeGen));
    }

    private TifChunkGenerator(BiomeSource biomeSource, GeneratorSettings settings, UndergroundLayerGenerator undergroundGen, UndergroundBiomeLayerGenerator undergroundBiomeGen, UndergroundBiomeSourceWrapper wrapper) {
        super(wrapper);
        wrapper.setGenerator(this);

        this.rawBiomeSource = biomeSource;
        this.settings = settings;
        this.undergroundGenerator = undergroundGen;
        this.undergroundBiomeGenerator = undergroundBiomeGen;

        ConfigManager.load();

        String terrainMapName = ConfigManager.configData.terrainBuilder.buildFromMap;
        File fallbackHeightFile = FMLPaths.GAMEDIR.get().resolve(settings.tiffPath()).toFile();
        String finalHeightPath = resolveMapPath(terrainMapName, fallbackHeightFile.getAbsolutePath());

        String waterMapName = ConfigManager.configData.waterBuilder.buildFromMap;
        File fallbackWaterFile = FMLPaths.GAMEDIR.get().resolve(settings.waterTiffPath()).toFile();
        String finalWaterPath = resolveMapPath(waterMapName, fallbackWaterFile.getAbsolutePath());

        String biomeMapName = ConfigManager.configData.biomeBuilder.buildFromMap;
        File fallbackForestFile = FMLPaths.GAMEDIR.get().resolve(settings.forestTiffPath()).toFile();
        String finalForestPath = resolveMapPath(biomeMapName, fallbackForestFile.getAbsolutePath());

        this.heightMapReader = new HeightMapReader(
                terrainMapName,
                finalHeightPath
        );
        this.waterLayer = new WaterLayer(
                waterMapName.isEmpty() || "Nothing".equals(waterMapName) ? "Nothing" : waterMapName,
                finalWaterPath
        );
        this.forestLayer = new ForestLayer(
                biomeMapName,
                finalForestPath
        );
        syncForestScale();

        String structureMapName = ConfigManager.configData.structureBuilder.buildFromMap;
        File fallbackStructureFile = FMLPaths.GAMEDIR.get().resolve(settings.forestTiffPath()).toFile();
        String finalStructurePath = resolveMapPath(structureMapName, fallbackStructureFile.getAbsolutePath());

        this.structureLayer = new StructureLayer(
                structureMapName.isEmpty() || "Nothing".equals(structureMapName) ? "White Map" : structureMapName,
                finalStructurePath
        );

        this.structureGenerator = new StructureLayerGenerator(structureLayer, ConfigManager.configData.structureBuilder.rules);
        this.undergroundStructureGenerator = new UndergroundStructureLayerGenerator(this.structureLayer);

        this.biomeLayerGenerator = new BiomeLayerGenerator(this.heightMapReader, this.waterLayer, this.forestLayer, this.biomeSource);

        this.pipeline = List.of(
                new TerrainLayerGenerator(() -> this.heightMapReader),
                new WaterLayerGenerator(this.waterLayer),
                new SeaLevelLayerGenerator()
        );
    }






    private void syncForestScale() {
        if (this.heightMapReader != null && this.forestLayer != null) {
            int heightW = this.heightMapReader.getTiffWidth();
            int forestW = this.forestLayer.getTiffWidth();
            if (heightW > 0 && forestW > 0) {
                this.forestLayer.setScale((double) heightW / forestW);
            }
        }
    }

    private String resolveMapPath(String mapName, String defaultPath) {
        if (mapName == null || mapName.isEmpty() || "Nothing".equals(mapName)) return "";
        if (ConfigManager.configData != null && ConfigManager.configData.maps != null) {
            for (ConfigManager.CustomMap cm : ConfigManager.configData.maps) {
                if (mapName.equals(cm.name)) {
                    return cm.path == null || cm.path.isEmpty() ? defaultPath : cm.path;
                }
            }
        }
        return defaultPath;
    }

    private volatile long lastConfigCheckMs = 0L;

    private void maybeUpdateReaders() {
        long now = System.currentTimeMillis();
        if (now - lastConfigCheckMs < 1000L) return;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastConfigCheckMs < 1000L) return;
            lastConfigCheckMs = now;
            updateReadersIfNeeded();
        }
    }

    public ColumnData getColumnData(int x, int z) {
        if (this.heightMapReader == null || this.heightMapReader.getPath().isEmpty()) {
            maybeUpdateReaders();
        }

        if (this.heightMapReader != null && this.heightMapReader.isOutOfBounds(x, z)) {
            return new ColumnData(getMinY() - 10);
        }

        ColumnData data = new ColumnData(0);
        for (LayerGenerator layerGenerator : pipeline) {
            layerGenerator.processColumn(x, z, data);
        }
        return data;
    }

    private boolean isHeightDataReady() {
        return this.heightMapReader != null && this.heightMapReader.isReady();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isHeightDataReady()) {
                maybeUpdateReaders();
                if (!isHeightDataReady()) {
                    throw new IllegalStateException("[TIFWORLDGEN] Heightmap reader not ready - chunk " + chunk.getPos() + " left ungenerated for retry");
                }
            }
            try {
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();

            ColumnData[][] chunkData = new ColumnData[16][16];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunkData[x][z] = getColumnData(minX + x, minZ + z);
                }
            }

            int minY = getMinY();
            int maxHeight = minY + getGenDepth() - 1;
            int seaLevel = ConfigManager.configData.waterBuilder.seaLevel;
            LevelChunkSection[] sections = chunk.getSections();

            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState water = Blocks.WATER.defaultBlockState();

            for (int secIdx = 0; secIdx < sections.length; secIdx++) {
                LevelChunkSection section = sections[secIdx];
                if (section == null) continue;

                int secMinY = chunk.getSectionYFromSectionIndex(secIdx) << 4;
                int secMaxY = secMinY + 15;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        ColumnData col = chunkData[x][z];

                        if (col.finalHeight < minY) continue;

                        int height = Math.min(maxHeight, col.finalHeight);
                        int depth = col.isWater ? Mth.clamp(col.waterDepth, 1, 16) : 0;
                        int stoneTop = Math.max(minY - 1, height - depth);
                        int waterTop = col.isWater
                                ? Math.max(height, Math.min(maxHeight, seaLevel))
                                : Math.min(maxHeight, seaLevel);

                        if (waterTop < stoneTop) waterTop = stoneTop;

                        if (secMinY > Math.max(stoneTop, waterTop)) continue;

                        int startY = Math.max(secMinY, minY);
                        int endY = Math.min(secMaxY, Math.max(stoneTop, waterTop));

                        for (int y = startY; y <= endY; y++) {
                            if (y <= stoneTop) {
                                section.setBlockState(x, y & 15, z & 15, stone, false);
                            } else {
                                section.setBlockState(x, y & 15, z & 15, water, false);
                            }
                        }
                    }
                }
            }

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    ColumnData col = chunkData[x][z];
                    if (col.finalHeight < minY) continue;

                    int height = Math.min(maxHeight, col.finalHeight);
                    int depth = col.isWater ? Mth.clamp(col.waterDepth, 1, 16) : 0;
                    int stoneTopY = Math.max(minY - 1, height - depth);
                    int waterSurface = col.isWater
                            ? Math.max(height, Math.min(maxHeight, seaLevel))
                            : Math.max(stoneTopY, Math.min(maxHeight, seaLevel));

                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x, waterSurface, z, stone);
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(x, stoneTopY, z, stone);
                }
            }

            return chunk;
            } catch (Throwable e) {
                System.err.println("[TIFWORLDGEN] Failed to generate chunk " + chunk.getPos() + " - using flat fallback terrain: " + e);
                e.printStackTrace();
                fillFlatFallback(chunk);
                return chunk;
            }
        }, executor);
    }

    private void fillFlatFallback(ChunkAccess chunk) {
        int minY = getMinY();
        int top = Math.min(minY + getGenDepth() - 1, minY + 40);
        BlockState stone = Blocks.STONE.defaultBlockState();
        LevelChunkSection[] sections = chunk.getSections();
        for (int secIdx = 0; secIdx < sections.length; secIdx++) {
            LevelChunkSection section = sections[secIdx];
            if (section == null) continue;
            int secMinY = chunk.getSectionYFromSectionIndex(secIdx) << 4;
            if (secMinY > top) break;
            for (int y = Math.max(secMinY, minY); y <= Math.min(top, secMinY + 15); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        section.setBlockState(x, y & 15, z & 15, stone, false);
                    }
                }
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x, top, z, stone);
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(x, top, z, stone);
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isHeightDataReady()) {
                throw new IllegalStateException("[TIFWORLDGEN] Heightmap reader not ready - biomes for chunk " + chunk.getPos() + " left for retry");
            }
            try {
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();

            int[][] surfaceHeights = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    surfaceHeights[x][z] = getColumnData(minX + x, minZ + z).finalHeight;
                }
            }

            @SuppressWarnings("unchecked")
            Holder<Biome>[] surfaceBiomeCache = new Holder[16];

            chunk.fillBiomesFromNoise((biomeX, biomeY, biomeZ, sampler) -> {
                Holder<Biome> vanillaBiome = this.biomeSource.getNoiseBiome(biomeX, biomeY, biomeZ, sampler);

                int blockY = biomeY << 2;
                int localX = (biomeX << 2) & 15;
                int localZ = (biomeZ << 2) & 15;

                int surfaceY = surfaceHeights[localX][localZ];

                if (blockY < surfaceY - 12) {
                    return vanillaBiome;
                }

                int cacheIdx = ((localX >> 2) << 2) + (localZ >> 2);
                if (surfaceBiomeCache[cacheIdx] == null) {
                    int blockX = minX + localX + 2;
                    int blockZ = minZ + localZ + 2;
                    surfaceBiomeCache[cacheIdx] = this.biomeLayerGenerator.getBiome(blockX, blockZ, vanillaBiome);
                }

                return surfaceBiomeCache[cacheIdx];
            }, randomState.sampler());

            return chunk;
            } catch (Throwable e) {
                System.err.println("[TIFWORLDGEN] Failed to assign biomes for chunk " + chunk.getPos() + " - using vanilla biomes: " + e);
                e.printStackTrace();
                chunk.fillBiomesFromNoise((bx, by, bz, sampler) -> this.biomeSource.getNoiseBiome(bx, by, bz, sampler), randomState.sampler());
                return chunk;
            }
        }, executor);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        try {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = getMinY();
        int maxHeight = minY + getGenDepth() - 1;
        int seaLevel = ConfigManager.configData.waterBuilder.seaLevel;

        int baseThreshold = ConfigManager.configData.terrainBuilder.baseSlopeThreshold;
        double yFactor = ConfigManager.configData.terrainBuilder.slopeThresholdYFactor;

        BlockState slopeState = Blocks.STONE.defaultBlockState();
        Block slopeBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(ConfigManager.configData.terrainBuilder.slopeBlock));
        if (slopeBlock != null) slopeState = slopeBlock.defaultBlockState();

        BlockState slopeUnderState = Blocks.STONE.defaultBlockState();
        Block slopeUnderBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(ConfigManager.configData.terrainBuilder.slopeUnderBlock));
        if (slopeUnderBlock != null) slopeUnderState = slopeUnderBlock.defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;
                ColumnData col = getColumnData(worldX, worldZ);

                if (col.finalHeight < minY) continue;
                int clampedFinal = Math.min(maxHeight, Math.max(minY, col.finalHeight));
                if (clampedFinal < minY) continue;

                int depth = col.isWater ? Mth.clamp(col.waterDepth, 1, 16) : 0;
                int topSolidY = Math.max(minY, clampedFinal - depth);

                int quartY = Mth.clamp((topSolidY + 2) >> 2, chunk.getMinSection() << 2, ((chunk.getMinSection() + chunk.getSections().length) << 2) - 1);
                String biomePath = chunk.getNoiseBiome(x >> 2, quartY, z >> 2)
                        .unwrapKey().map(key -> key.location().getPath()).orElse("");

                BlockState biomeSurface = null;
                BlockState biomeUnder = null;
                boolean snowLayer = false;
                if (biomePath.contains("desert")) {
                    biomeSurface = Blocks.SAND.defaultBlockState();
                    biomeUnder = Blocks.SANDSTONE.defaultBlockState();
                } else if (biomePath.equals("beach") || biomePath.equals("snowy_beach")) {
                    biomeSurface = Blocks.SAND.defaultBlockState();
                    biomeUnder = Blocks.SAND.defaultBlockState();
                } else if (biomePath.startsWith("badlands")) {
                    biomeSurface = Blocks.TERRACOTTA.defaultBlockState();
                    biomeUnder = Blocks.TERRACOTTA.defaultBlockState();
                } else if (biomePath.startsWith("snowy") || biomePath.equals("grove")
                        || biomePath.equals("frozen_peaks") || biomePath.equals("jagged_peaks")
                        || biomePath.equals("snowy_slopes")) {
                    snowLayer = true;
                }

                pos.set(x, topSolidY, z);
                BlockState currentState = chunk.getBlockState(pos);
                if (currentState.is(Blocks.STONE) || currentState.is(Blocks.DEEPSLATE)) {

                    long edgeHash = worldX * 341873128712L + worldZ * 132897987541L;
                    edgeHash ^= edgeHash >>> 13; edgeHash *= 0x5BD1E995L; edgeHash ^= edgeHash >>> 15;
                    double slopeJitter = (edgeHash & 0xFFFFL) / 65535.0 * 1.5;

                    double dynamicThreshold = Math.max(1.0, baseThreshold - ((clampedFinal - minY) * yFactor)) + slopeJitter;

                    int h = col.finalHeight;
                    boolean isSteep = Math.abs(getColumnData(worldX + 1, worldZ).finalHeight - h) > dynamicThreshold ||
                            Math.abs(getColumnData(worldX - 1, worldZ).finalHeight - h) > dynamicThreshold ||
                            Math.abs(getColumnData(worldX, worldZ + 1).finalHeight - h) > dynamicThreshold ||
                            Math.abs(getColumnData(worldX, worldZ - 1).finalHeight - h) > dynamicThreshold;

                    if (col.isWater || clampedFinal < seaLevel) {
                        chunk.setBlockState(pos, col.surfaceBlock, false);
                    } else if (isSteep) {
                        chunk.setBlockState(pos, slopeState, false);

                        pos.set(x, topSolidY - 1, z);
                        if (chunk.getBlockState(pos).is(Blocks.STONE) || chunk.getBlockState(pos).is(Blocks.DEEPSLATE)) {
                            chunk.setBlockState(pos, slopeUnderState, false);
                        }
                        pos.set(x, topSolidY, z);
                    } else {
                        chunk.setBlockState(pos, biomeSurface != null ? biomeSurface : col.surfaceBlock, false);
                        if (snowLayer && topSolidY < maxHeight) {
                            pos.set(x, topSolidY + 1, z);
                            if (chunk.getBlockState(pos).isAir()) {
                                chunk.setBlockState(pos, Blocks.SNOW.defaultBlockState(), false);
                            }
                            pos.set(x, topSolidY, z);
                        }
                    }
                }

                for (int i = 1; i <= 4; i++) {
                    int y = topSolidY - i;
                    if (y >= minY) {
                        pos.set(x, y, z);
                        BlockState subState = chunk.getBlockState(pos);
                        if (subState.is(Blocks.STONE) || subState.is(Blocks.DEEPSLATE)) {
                            chunk.setBlockState(pos, biomeUnder != null ? biomeUnder : col.underBlock, false);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        } catch (Exception e) {
            System.err.println("[TIFWORLDGEN] Failed to build surface for chunk " + chunk.getPos() + " (chunk left as stone): " + e);
            e.printStackTrace();
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carvingStep) {
        if (carvingStep == GenerationStep.Carving.AIR) {
            if (ConfigManager.configData.undergroundBuilder == null || !ConfigManager.configData.undergroundBuilder.generateCaves) {
                return;
            }
            try {
                this.undergroundGenerator.generate(chunk, randomState.router(), this, seed, structureManager);
            } catch (Exception e) {
                System.err.println("[TIFWORLDGEN] Failed to carve caves for chunk " + chunk.getPos() + " (caves skipped): " + e);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        this.worldSeed = level.getSeed();
        try {
            super.applyBiomeDecoration(level, chunk, structureManager);
        } catch (Exception e) {
            System.err.println("[TIFWORLDGEN] Failed to decorate chunk " + chunk.getPos() + " (decorations skipped, chunk kept): " + e);
            e.printStackTrace();
        }
        try {
            this.undergroundBiomeGenerator.decorateUndergroundBiomes(chunk, this, getMinY(), level.getSeed());
        } catch (Exception e) {
            System.err.println("[TIFWORLDGEN] Failed underground biome decoration for chunk " + chunk.getPos() + ": " + e);
            e.printStackTrace();
        }
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        ColumnData col = getColumnData(x, z);
        int minY = getMinY();
        int maxHeight = minY + getGenDepth() - 1;
        int seaLevel = getSeaLevel();

        int clampedFinal = Mth.clamp(col.finalHeight, minY, maxHeight);
        int depth = col.isWater ? Mth.clamp(col.waterDepth, 1, 16) : 0;
        int stoneTop = Math.max(minY - 1, clampedFinal - depth);
        int clampedWater = col.isWater
                ? Math.max(clampedFinal, Mth.clamp(seaLevel, minY, maxHeight))
                : Mth.clamp(seaLevel, minY, maxHeight);

        int topY = Math.max(stoneTop, clampedWater);
        int generationHeight = Math.max(0, topY - minY + 1);
        BlockState[] states = new BlockState[generationHeight];

        for (int y = 0; y < generationHeight; y++) {
            int worldY = minY + y;
            if (worldY <= stoneTop) {
                states[y] = Blocks.STONE.defaultBlockState();
            } else if (col.isWater && worldY <= clampedWater) {
                states[y] = Blocks.WATER.defaultBlockState();
            } else {
                states[y] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor heightAccessor, RandomState randomState) {
        ColumnData col = getColumnData(x, z);
        int minY = getMinY();
        int maxHeight = minY + getGenDepth() - 1;

        int targetHeight;
        if (types == Heightmap.Types.OCEAN_FLOOR || types == Heightmap.Types.OCEAN_FLOOR_WG) {
            int depth = col.isWater ? Mth.clamp(col.waterDepth, 1, 16) : 0;
            targetHeight = col.finalHeight - depth;
        } else {
            int seaLevel = getSeaLevel();
            targetHeight = col.isWater ? Math.max(col.finalHeight, seaLevel) : col.finalHeight;
        }

        return Mth.clamp(targetHeight, minY, maxHeight);
    }

    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    public record GeneratorSettings(String tiffPath, String waterTiffPath, String forestTiffPath, int minY, int height) {
        public static final Codec<GeneratorSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("tiff_path").forGetter(GeneratorSettings::tiffPath),
                Codec.STRING.fieldOf("water_tiff_path").forGetter(GeneratorSettings::waterTiffPath),
                Codec.STRING.fieldOf("forest_tiff_path").forGetter(GeneratorSettings::forestTiffPath),
                Codec.INT.fieldOf("min_y").forGetter(GeneratorSettings::minY),
                Codec.INT.fieldOf("height").forGetter(GeneratorSettings::height)
        ).apply(instance, GeneratorSettings::new));
    }

    public static final Codec<TifChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.rawBiomeSource),
            GeneratorSettings.CODEC.fieldOf("settings").forGetter(gen -> gen.settings)
    ).apply(instance, TifChunkGenerator::new));

    @Override public int getMinY() { return settings.minY(); }
    @Override public int getGenDepth() { return settings.height(); }
    @Override public int getSeaLevel() {
        if (ConfigManager.configData != null && ConfigManager.configData.waterBuilder != null) {
            return ConfigManager.configData.waterBuilder.seaLevel;
        }
        return 63;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
        this.worldSeed = structureState.getLevelSeed();
        try {
            super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager);

            this.structureGenerator.filterStructures(chunk);
            this.undergroundStructureGenerator.filterUndergroundStructures(chunk);
        } catch (Exception e) {
            System.err.println("[TIFWORLDGEN] Failed to process structures for chunk " + chunk.getPos() + ": " + e);
            e.printStackTrace();
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        try {
            ChunkPos center = region.getCenter();
            Holder<Biome> biome = region.getBiome(center.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(region, biome, center, random);
        } catch (Exception e) {
            System.err.println("[TIFWORLDGEN] Failed to spawn mobs for chunk " + region.getCenter() + ": " + e);
            e.printStackTrace();
        }
    }
    @Override public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {}

    public List<LayerGenerator> getPipeline() { return this.pipeline; }
    public ForestLayer getForestLayer() { return this.forestLayer; }
    public HeightMapReader getHeightMapReader() { return this.heightMapReader; }
    public WaterLayer getWaterLayer() { return this.waterLayer; }
    public StructureLayer getStructureLayer() { return this.structureLayer; }
    public UndergroundLayerGenerator getUndergroundGenerator() { return this.undergroundGenerator; }
    public UndergroundBiomeLayerGenerator getUndergroundBiomeGenerator() { return this.undergroundBiomeGenerator; }

    public void close() {
        try {
            if (this.heightMapReader != null) this.heightMapReader.close();
            if (this.waterLayer != null) this.waterLayer.close();
            if (this.forestLayer != null) this.forestLayer.close();
            if (this.structureLayer != null) this.structureLayer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Holder<Biome> getSurfaceBiomeAt(int blockX, int blockZ, Holder<Biome> fallback) {
        if (this.biomeLayerGenerator != null) {
            return this.biomeLayerGenerator.getBiome(blockX, blockZ, fallback);
        }
        return fallback;
    }

    public static class UndergroundBiomeSourceWrapper extends BiomeSource {
        private final BiomeSource delegate;
        private final UndergroundBiomeLayerGenerator undergroundBiomeGen;
        private TifChunkGenerator generator;
        private final Map<ResourceKey<Biome>, Holder<Biome>> biomeHoldersCache = new ConcurrentHashMap<>();

        public UndergroundBiomeSourceWrapper(BiomeSource delegate, UndergroundBiomeLayerGenerator undergroundBiomeGen) {
            this.delegate = delegate;
            this.undergroundBiomeGen = undergroundBiomeGen;
        }

        public void setGenerator(TifChunkGenerator generator) {
            this.generator = generator;
        }

        @Override
        protected Codec<? extends BiomeSource> codec() {
            return Codec.unit(this.delegate);
        }

        @Override
        protected java.util.stream.Stream<Holder<Biome>> collectPossibleBiomes() {
            return delegate.possibleBiomes().stream();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, net.minecraft.world.level.biome.Climate.Sampler sampler) {
            int blockX = quartX << 2;
            int blockY = quartY << 2;
            int blockZ = quartZ << 2;

            long worldSeed = 0L;
            if (this.generator != null) {
                worldSeed = this.generator.getSettingsSeed();
            }
            this.undergroundBiomeGen.ensureInitialized(worldSeed);

            int surfaceY = 64;
            if (this.generator != null) {
                surfaceY = this.generator.getColumnData(blockX, blockZ).finalHeight;
            }

            ResourceKey<Biome> undergroundKey = this.undergroundBiomeGen.getUndergroundBiomeKey(blockX, blockY, blockZ, surfaceY, worldSeed);

            if (undergroundKey != null) {
                Holder<Biome> holder = biomeHoldersCache.computeIfAbsent(undergroundKey, key ->
                        delegate.possibleBiomes().stream()
                                .filter(h -> h.is(key))
                                .findFirst()
                                .orElse(null)
                );

                if (holder != null) {
                    return holder;
                }
            }

            if (this.generator != null) {
                Holder<Biome> vanillaFallback = delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
                return this.generator.getSurfaceBiomeAt(blockX, blockZ, vanillaFallback);
            }

            return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
    }

    public long getSettingsSeed() {
        return this.worldSeed;
    }

    public synchronized void updateReadersIfNeeded() {
        ConfigManager.load();
        if (ConfigManager.configData == null) return;

        String newHeightPath = resolveMapPath(ConfigManager.configData.terrainBuilder.buildFromMap, "");
        if (!newHeightPath.isEmpty() && !newHeightPath.equals(this.heightMapReader.getPath())) {
            try {
                this.heightMapReader.close();
                this.heightMapReader = new HeightMapReader(ConfigManager.configData.terrainBuilder.buildFromMap, newHeightPath);
            } catch (Exception e) { e.printStackTrace(); }
        }

        String waterMapName = ConfigManager.configData.waterBuilder.buildFromMap;
        String newWaterPath = resolveMapPath(waterMapName, "");
        if (!waterMapName.equals(this.waterLayer.getMapName()) || !newWaterPath.equals(this.waterLayer.getPath())) {
            try {
                this.waterLayer.close();
                this.waterLayer = new WaterLayer(
                        waterMapName.isEmpty() || "Nothing".equals(waterMapName) ? "Nothing" : waterMapName,
                        newWaterPath
                );
            } catch (Exception e) { e.printStackTrace(); }
        }

        String forestMapName = ConfigManager.configData.biomeBuilder.buildFromMap;
        String newForestPath = resolveMapPath(forestMapName, "");
        if (!forestMapName.equals(this.forestLayer.getMapName()) || !newForestPath.equals(this.forestLayer.getPath())) {
            try {
                this.forestLayer.close();
                this.forestLayer = new ForestLayer(
                        forestMapName,
                        newForestPath
                );
                syncForestScale();
            } catch (Exception e) { e.printStackTrace(); }
        }

        String structureMapName = ConfigManager.configData.structureBuilder.buildFromMap;
        String newStructurePath = resolveMapPath(structureMapName, "");
        if (!structureMapName.equals(this.structureLayer.getMapName()) || !newStructurePath.equals(this.structureLayer.getPath())) {
            try {
                this.structureLayer.close();
                this.structureLayer = new StructureLayer(
                        structureMapName.isEmpty() || "Nothing".equals(structureMapName) ? "White Map" : structureMapName,
                        newStructurePath
                );
                this.structureGenerator = new StructureLayerGenerator(this.structureLayer, ConfigManager.configData.structureBuilder.rules);
                this.undergroundStructureGenerator = new UndergroundStructureLayerGenerator(this.structureLayer);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}