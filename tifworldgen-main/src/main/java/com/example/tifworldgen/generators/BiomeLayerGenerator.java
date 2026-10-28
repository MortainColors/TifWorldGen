package com.example.tifworldgen.generators;

import com.example.tifworldgen.config.ConfigManager;
import com.example.tifworldgen.layers.WaterLayer;
import com.example.tifworldgen.layers.ForestLayer;
import com.example.tifworldgen.layers.utils.HeightMapReader;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeLayerGenerator {
    private final HeightMapReader heightMapReader;
    private final WaterLayer waterLayer;
    private final ForestLayer forestLayer;
    private final BiomeSource biomeSource;

    private final Map<ResourceKey<Biome>, Holder<Biome>> biomeCache = new ConcurrentHashMap<>();

    public BiomeLayerGenerator(HeightMapReader heightMapReader, WaterLayer waterLayer, ForestLayer forestLayer, BiomeSource biomeSource) {
        this.heightMapReader = heightMapReader;
        this.waterLayer = waterLayer;
        this.forestLayer = forestLayer;
        this.biomeSource = biomeSource;
        if (ConfigManager.configData.biomeBuilder.rules.isEmpty()) {
            ConfigManager.load();
        }
    }

    private Holder<Biome> getBiomeHolder(ResourceKey<Biome> key, Holder<Biome> fallback) {
        Holder<Biome> cached = biomeCache.get(key);
        if (cached != null) return cached;
        for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
            if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(key)) {
                biomeCache.putIfAbsent(key, holder);
                return holder;
            }
        }
        return fallback;
    }

    private Holder<Biome> getBiomeHolderFromId(String biomeId, Holder<Biome> fallback) {
        try {
            String cleanId = biomeId.trim();
            ResourceLocation loc = ResourceLocation.tryParse(cleanId);
            if (loc == null) return getBiomeHolder(Biomes.PLAINS, fallback);

            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, loc);
            Holder<Biome> cached = biomeCache.get(key);
            if (cached != null) return cached;

            for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
                if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(key)) {
                    biomeCache.putIfAbsent(key, holder); return holder;
                }
            }

            Holder<Biome> forgeHolder = ForgeRegistries.BIOMES.getDelegate(key.location()).orElse(null);
            if (forgeHolder != null) {
                biomeCache.putIfAbsent(key, forgeHolder);
                return forgeHolder;
            }

            return getBiomeHolder(Biomes.PLAINS, fallback);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return getBiomeHolder(Biomes.PLAINS, fallback);
    }

    private static final int NO_TERRAIN_DATA = Integer.MIN_VALUE;

    private int getTerrainHeight(int x, int z) {
        if (ConfigManager.configData == null || ConfigManager.configData.terrainBuilder == null) return NO_TERRAIN_DATA;
        if ("Nothing".equals(ConfigManager.configData.terrainBuilder.buildFromMap)) return NO_TERRAIN_DATA;
        return heightMapReader.getHeight(x, z);
    }

    public Holder<Biome> getBiome(int blockX, int blockZ, Holder<Biome> vanillaFallback) {
        if (ConfigManager.configData == null) return vanillaFallback;

        int surfaceY = getTerrainHeight(blockX, blockZ);
        int seaLevel = ConfigManager.configData.waterBuilder.seaLevel;

        if (surfaceY != NO_TERRAIN_DATA && surfaceY < seaLevel) {
            return getBiomeHolder(Biomes.OCEAN, vanillaFallback);
        }

        if (waterLayer != null && waterLayer.isWater(blockX, blockZ)) {
            return getBiomeHolder(isLargeWaterArea(blockX, blockZ) ? Biomes.OCEAN : Biomes.RIVER, vanillaFallback);
        }

        String mapName = ConfigManager.configData.biomeBuilder.buildFromMap;
        if (mapName != null && !mapName.isEmpty() && !"Nothing".equalsIgnoreCase(mapName)) {
            return getProcessedBiome(blockX, blockZ, vanillaFallback);
        }

        return vanillaFallback;
    }

    /**
     * Rozlíši rieku od jazera: vzorkuje vodnú masku v 8 smeroch vo vzdialenosti
     * 40 blokov. Ak je voda takmer všade okolo, ide o veľkú vodnú plochu (jazero)
     * -> ocean biom (kelp, ryby). Úzky vodný pás -> river biom.
     */
    private boolean isLargeWaterArea(int blockX, int blockZ) {
        final int radius = 40;
        int waterSamples = 0;
        if (waterLayer.isWater(blockX + radius, blockZ)) waterSamples++;
        if (waterLayer.isWater(blockX - radius, blockZ)) waterSamples++;
        if (waterLayer.isWater(blockX, blockZ + radius)) waterSamples++;
        if (waterLayer.isWater(blockX, blockZ - radius)) waterSamples++;
        if (waterSamples < 4) return false;
        if (waterLayer.isWater(blockX + radius, blockZ + radius)) waterSamples++;
        if (waterLayer.isWater(blockX + radius, blockZ - radius)) waterSamples++;
        if (waterLayer.isWater(blockX - radius, blockZ + radius)) waterSamples++;
        if (waterLayer.isWater(blockX - radius, blockZ - radius)) waterSamples++;
        return waterSamples >= 6;
    }

    private Holder<Biome> getProcessedBiome(int blockX, int blockZ, Holder<Biome> vanillaFallback) {
        int distortedX = blockX + (int)(Math.sin(blockZ * 0.05) * 10) + (int)(Math.sin(blockX * 0.1) * 3);
        int distortedZ = blockZ + (int)(Math.cos(blockX * 0.05) * 10) + (int)(Math.cos(blockZ * 0.1) * 3);
        int y = getTerrainHeight(blockX, blockZ);

        if (ConfigManager.configData.biomeBuilder != null && ConfigManager.configData.biomeBuilder.rules != null) {
            int rawPixel = forestLayer.getRawPixelValue(distortedX, distortedZ);
            String hexString = forestLayer.getHexColor(distortedX, distortedZ);

            int hexInt = 0;
            try { hexInt = Integer.parseInt(hexString.replace("#", ""), 16); } catch (Exception ignored) {}

            List<ConfigManager.BiomeRule> rules = ConfigManager.configData.biomeBuilder.rules;
            for (ConfigManager.BiomeRule rule : rules) {
                if (rule == null) continue;

                boolean match = false;
                if ("COLOR".equals(rule.ruleType)) {
                    int ruleHex = 0;
                    try { ruleHex = Integer.parseInt(rule.hexColor.replace("#", ""), 16); } catch (Exception ignored) {}

                    int r1 = (hexInt >> 16) & 0xFF; int g1 = (hexInt >> 8) & 0xFF; int b1 = hexInt & 0xFF;
                    int r2 = (ruleHex >> 16) & 0xFF; int g2 = (ruleHex >> 8) & 0xFF; int b2 = ruleHex & 0xFF;

                    int colorDistance = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                    match = colorDistance <= 30;
                } else if ("MATRIX".equals(rule.ruleType)) {
                    match = (rawPixel == rule.forestValue);
                }

                if (match && y >= rule.minY && y <= rule.maxY) {
                    return getBiomeHolderFromId(rule.targetBiome, vanillaFallback);
                }
            }
        }

        return getBiomeHolder(Biomes.PLAINS, vanillaFallback);
    }
}