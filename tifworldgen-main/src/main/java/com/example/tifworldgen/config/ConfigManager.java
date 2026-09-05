package com.example.tifworldgen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    public static Data configData = new Data();

    public static class CustomMap {
        public String name;
        public String path;
        public CustomMap(String name, String path) { this.name = name; this.path = path; }
    }

    public static class BiomeRule {
        public int forestValue = 0;
        public String ruleType = "MATRIX";
        public int minY = 0;
        public int maxY = 256;
        public int forestType = 0;
        public String hexColor = "#FFFFFF";
        public String targetBiome = "minecraft:plains";
        public BiomeRule() { this.ruleType = "MATRIX"; }
    }

    public static class StructureRule {
        public String hexColor = "#FFFFFF";
        public String targetStructure = "minecraft:village";
    }

    public static class TerrainBuilderConfig {
        public String buildFromMap = "";
        public String bitDepth = "uint16";
        public int minY = -64;
        public int maxY = 320;

        public int mapMinHeight = -999999;
        public int mapMaxHeight = -999999;

        public int baseSlopeThreshold = 4;
        public double slopeThresholdYFactor = 0.02;
        public String slopeBlock = "minecraft:stone";
        public String slopeUnderBlock = "minecraft:stone";
    }

    public static class WaterBuilderConfig {
        public String buildFromMap = "";
        public int seaLevel = 63;
    }

    public static class BiomeBuilderConfig {
        public String buildFromMap = "";
        public List<BiomeRule> rules = new ArrayList<>();
    }

    public static class StructureBuilderConfig {
        public String buildFromMap = "White Map";
        public List<StructureRule> rules = new ArrayList<>();
    }

    public static class UndergroundBuilderConfig {
        public int averageSurfaceY = 1000;
        public boolean generateCaves = true;
    }

    public static class SeasonConfig {
        public int winterFreezeY = 200;
        public int summerFreezeY = 3000;
        public int springAutumnFreezeY = 1300;
    }

    public static class Data {
        public String heightmapName = "Heightmap TIFF";
        public String heightmapPath = "";
        public boolean showHeightmap = true;
        public String forestmapName = "Forest Layer TIFF";
        public String forestmapPath = "";
        public boolean showForestmap = true;
        public String watermapName = "Water/River TIFF";
        public String watermapPath = "";
        public boolean showWatermap = true;
        public List<CustomMap> customMaps = new ArrayList<>();
        public TerrainBuilderConfig terrainBuilder = new TerrainBuilderConfig();
        public WaterBuilderConfig waterBuilder = new WaterBuilderConfig();
        public BiomeBuilderConfig biomeBuilder = new BiomeBuilderConfig();
        public StructureBuilderConfig structureBuilder = new StructureBuilderConfig();
        public UndergroundBuilderConfig undergroundBuilder = new UndergroundBuilderConfig();
        public SeasonConfig seasonBuilder = new SeasonConfig();
    }

    public static void init() {
        File configDir = FMLPaths.CONFIGDIR.get().toFile();
        if (!configDir.exists()) configDir.mkdirs();
        configFile = new File(configDir, "tifworldgen.json");
        if (!configFile.exists()) {
            configData = new Data();
            save();
        } else {
            load();
        }
    }

    public static void load() {
        if (configFile == null || !configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            configData = GSON.fromJson(reader, Data.class);
            if (configData == null) configData = new Data();
            if (configData.customMaps == null) configData.customMaps = new ArrayList<>();
            if (configData.terrainBuilder == null) configData.terrainBuilder = new TerrainBuilderConfig();
            if (configData.waterBuilder == null) configData.waterBuilder = new WaterBuilderConfig();
            if (configData.biomeBuilder == null) configData.biomeBuilder = new BiomeBuilderConfig();
            if (configData.biomeBuilder.rules == null) configData.biomeBuilder.rules = new ArrayList<>();

            if (configData.structureBuilder == null) configData.structureBuilder = new StructureBuilderConfig();
            if (configData.structureBuilder.rules == null) configData.structureBuilder.rules = new ArrayList<>();

            if (configData.undergroundBuilder == null) configData.undergroundBuilder = new UndergroundBuilderConfig();
            if (configData.seasonBuilder == null) configData.seasonBuilder = new SeasonConfig();

            for (BiomeRule rule : configData.biomeBuilder.rules) {
                if (rule.ruleType == null || rule.ruleType.isEmpty()) rule.ruleType = "MATRIX";
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        if (configFile == null) return;
        try (FileWriter writer = new FileWriter(configFile)) { GSON.toJson(configData, writer); } catch (Exception e) { e.printStackTrace(); }
    }
}