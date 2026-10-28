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
        public String heightmapName;
        public String heightmapPath;
        public Boolean showHeightmap;
        public String forestmapName;
        public String forestmapPath;
        public Boolean showForestmap;
        public String watermapName;
        public String watermapPath;
        public Boolean showWatermap;
        public List<CustomMap> customMaps;
        public List<CustomMap> maps = new ArrayList<>();
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
            if (configData.maps == null) configData.maps = new ArrayList<>();
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

            if (migrateLegacyMaps()) {
                System.out.println("[TIFWORLDGEN] Config migrovaný na zjednotený zoznam máp (" + configData.maps.size() + " máp).");
                save();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Migrácia starého configu (trojica pevných slotov + customMaps) na jednotný
     * zoznam maps[]. Vracia true, ak sa niečo zmenilo (a config treba uložiť).
     * Po migrácii sa staré polia nulujú, aby sa do uloženého JSON už nezapisovali.
     */
    private static boolean migrateLegacyMaps() {
        boolean changed = false;

        if (configData.heightmapName != null || configData.heightmapPath != null
                || configData.forestmapName != null || configData.forestmapPath != null
                || configData.watermapName != null || configData.watermapPath != null
                || (configData.customMaps != null && !configData.customMaps.isEmpty())) {
            changed = true;
        }

        absorbLegacyMap(configData.heightmapName, configData.heightmapPath);
        absorbLegacyMap(configData.forestmapName, configData.forestmapPath);
        absorbLegacyMap(configData.watermapName, configData.watermapPath);
        if (configData.customMaps != null) {
            for (CustomMap cm : configData.customMaps) absorbLegacyMap(cm.name, cm.path);
        }

        configData.heightmapName = null;
        configData.heightmapPath = null;
        configData.showHeightmap = null;
        configData.forestmapName = null;
        configData.forestmapPath = null;
        configData.showForestmap = null;
        configData.watermapName = null;
        configData.watermapPath = null;
        configData.showWatermap = null;
        configData.customMaps = null;

        return changed;
    }

    private static void absorbLegacyMap(String name, String path) {
        if (path == null || path.isEmpty()) return;
        String mapName = (name == null || name.isEmpty()) ? "Map " + (configData.maps.size() + 1) : name;
        for (CustomMap m : configData.maps) {
            if (m.name.equals(mapName)) {
                m.path = path;
                return;
            }
        }
        configData.maps.add(new CustomMap(mapName, path));
    }

    public static void save() {
        if (configFile == null) return;
        try (FileWriter writer = new FileWriter(configFile)) { GSON.toJson(configData, writer); } catch (Exception e) { e.printStackTrace(); }
    }
}