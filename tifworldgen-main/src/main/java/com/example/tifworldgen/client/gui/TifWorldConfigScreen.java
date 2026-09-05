package com.example.tifworldgen.client.gui;

import com.example.tifworldgen.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TifWorldConfigScreen extends Screen {

    private final Screen lastScreen;
    private int activeTab = 0;

    private double scrollAmount = 0;
    private double popupScrollAmount = 0;

    private boolean isBiomePopupActive = false;
    private boolean isStructurePopupActive = false;
    private boolean isSlopePopupActive = false;

    private Button tab1Btn, tab2Btn, tab3Btn, doneButton, addButton, backButton;
    private Button savePresetBtn, loadPresetBtn;
    private Button slopeCloseBtn, slopeBackBtn;
    private Button caveToggleBtn;


    private EditBox heightNameBox, heightMapBox; Button heightBrowseBtn, heightDeleteBtn;
    private EditBox forestNameBox, forestMapBox; Button forestBrowseBtn, forestDeleteBtn;
    private EditBox waterNameBox, waterMapBox; Button waterBrowseBtn, waterDeleteBtn;
    private EditBox slopeBaseThresholdBox, slopeYFactorBox, slopeBlockBox, slopeUnderBlockBox;
    private final List<CustomRowComponents> uiCustomRows = new ArrayList<>();

    private Button terrainMapBtn, terrainSlopeBtn; private EditBox terrainMinYBox, terrainMaxYBox;
    private Button waterMapBtn; private EditBox waterSeaLevelBox;
    private Button biomeMapBtn, biomeConfigureBtn;
    private Button structureMapBtn, structConfigureBtn;



    private EditBox undergroundAvgSurfaceYBox;

    private EditBox summerFreezeYBox, winterFreezeYBox, springAutumnFreezeYBox;

    private static class PopupInnerRow {
        Button typeBtn;
        EditBox minYBox, maxYBox;
        EditBox forestValueBox;
        EditBox hexBox;
        EditBox biomeBox;
        Button deleteButton;
    }
    private final List<PopupInnerRow> uiPopupRows = new ArrayList<>();
    private Button popupAddBtn, popupCloseBtn;



    private static class StructPopupRow {
        EditBox hexBox;
        EditBox structBox;
        Button deleteButton;
    }

    private final List<StructPopupRow> uiStructPopupRows = new ArrayList<>();
    private Button structPopupAddBtn, structPopupCloseBtn, structBackButton;

    private static class CustomRowComponents {
        EditBox nameBox; EditBox pathBox; Button browseButton; Button deleteButton;
    }

    public TifWorldConfigScreen(Screen lastScreen) {
        super(Component.literal("Tifworldgen Generation Config Center"));
        this.lastScreen = lastScreen;
        ConfigManager.init();
    }

    private List<String> getCurrentlyAvailableMapNames() {
        List<String> names = new ArrayList<>();
        names.add("White Map");
        names.add("Black Map");

        if (ConfigManager.configData.showHeightmap && heightNameBox != null && !heightNameBox.getValue().isEmpty()) {
            names.add(heightNameBox.getValue());
        } else if (ConfigManager.configData.showHeightmap && (heightNameBox == null) && !ConfigManager.configData.heightmapName.isEmpty()) {
            names.add(ConfigManager.configData.heightmapName);
        }

        if (ConfigManager.configData.showForestmap && forestNameBox != null && !forestNameBox.getValue().isEmpty()) {
            names.add(forestNameBox.getValue());
        } else if (ConfigManager.configData.showForestmap && (forestNameBox == null) && !ConfigManager.configData.forestmapName.isEmpty()) {
            names.add(ConfigManager.configData.forestmapName);
        }

        if (ConfigManager.configData.showWatermap && waterNameBox != null && !waterNameBox.getValue().isEmpty()) {
            names.add(waterNameBox.getValue());
        } else if (ConfigManager.configData.showWatermap && (waterNameBox == null) && !ConfigManager.configData.watermapName.isEmpty()) {
            names.add(ConfigManager.configData.watermapName);
        }

        if (!uiCustomRows.isEmpty()) {
            for (CustomRowComponents row : uiCustomRows) {
                if (row.nameBox != null && !row.nameBox.getValue().isEmpty()) {
                    names.add(row.nameBox.getValue());
                }
            }
        } else {
            for (ConfigManager.CustomMap cm : ConfigManager.configData.customMaps) {
                if (cm.name != null && !cm.name.isEmpty()) names.add(cm.name);
            }
        }

        return names;
    }

    @Override
    protected void init() {
        int dummyY = 70;
        int nameX = 15, nameWidth = 115, boxX = 140, browseWidth = 50, deleteWidth = 20;
        int boxWidth = this.width - boxX - browseWidth - deleteWidth - 25;
        int browseX = boxX + boxWidth + 5; int deleteX = browseX + browseWidth + 5;

        if (ConfigManager.configData.showHeightmap) {
            this.heightNameBox = new EditBox(this.font, nameX, dummyY, nameWidth, 20, Component.literal("Heightmap Name"));
            this.heightNameBox.setValue(ConfigManager.configData.heightmapName);
            this.heightMapBox = new EditBox(this.font, boxX, dummyY, boxWidth, 20, Component.literal("Heightmap Path"));
            this.heightMapBox.setMaxLength(512); this.heightMapBox.setValue(ConfigManager.configData.heightmapPath);
            this.heightBrowseBtn = Button.builder(Component.literal("Browse..."), b -> openFilePicker(this.heightMapBox)).bounds(browseX, dummyY, browseWidth, 20).build();
            this.heightDeleteBtn = Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.showHeightmap = false; ConfigManager.configData.heightmapPath = ""; this.init(); }).bounds(deleteX, dummyY, deleteWidth, 20).build();
            dummyY += 25;
        } else { this.heightNameBox = null; this.heightMapBox = null; }

        if (ConfigManager.configData.showForestmap) {
            this.forestNameBox = new EditBox(this.font, nameX, dummyY, nameWidth, 20, Component.literal("Forestmap Name"));
            this.forestNameBox.setValue(ConfigManager.configData.forestmapName);
            this.forestMapBox = new EditBox(this.font, boxX, dummyY, boxWidth, 20, Component.literal("Forestmap Path"));
            this.forestMapBox.setMaxLength(512); this.forestMapBox.setValue(ConfigManager.configData.forestmapPath);
            this.forestBrowseBtn = Button.builder(Component.literal("Browse..."), b -> openFilePicker(this.forestMapBox)).bounds(browseX, dummyY, browseWidth, 20).build();
            this.forestDeleteBtn = Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.showForestmap = false; ConfigManager.configData.forestmapPath = ""; this.init(); }).bounds(deleteX, dummyY, deleteWidth, 20).build();
            dummyY += 25;
        } else { this.forestNameBox = null; this.forestMapBox = null; }

        if (ConfigManager.configData.showWatermap) {
            this.waterNameBox = new EditBox(this.font, nameX, dummyY, nameWidth, 20, Component.literal("Watermap Name"));
            this.waterNameBox.setValue(ConfigManager.configData.watermapName);
            this.waterMapBox = new EditBox(this.font, boxX, dummyY, boxWidth, 20, Component.literal("Watermap Path"));
            this.waterMapBox.setMaxLength(512); this.waterMapBox.setValue(ConfigManager.configData.watermapPath);
            this.waterBrowseBtn = Button.builder(Component.literal("Browse..."), b -> openFilePicker(this.waterMapBox)).bounds(browseX, dummyY, browseWidth, 20).build();
            this.waterDeleteBtn = Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.showWatermap = false; ConfigManager.configData.watermapPath = ""; this.init(); }).bounds(deleteX, dummyY, deleteWidth, 20).build();
            dummyY += 25;
        } else { this.waterNameBox = null; this.waterMapBox = null; }

        this.uiCustomRows.clear();
        for (int i = 0; i < ConfigManager.configData.customMaps.size(); i++) {
            final int index = i; ConfigManager.CustomMap mapData = ConfigManager.configData.customMaps.get(i); CustomRowComponents row = new CustomRowComponents();
            row.nameBox = new EditBox(this.font, nameX, dummyY, nameWidth, 20, Component.literal("Map Name")); row.nameBox.setValue(mapData.name);
            row.pathBox = new EditBox(this.font, boxX, dummyY, boxWidth, 20, Component.literal("Map Path")); row.pathBox.setMaxLength(512); row.pathBox.setValue(mapData.path);
            EditBox pBox = row.pathBox; row.browseButton = Button.builder(Component.literal("Browse..."), b -> openFilePicker(pBox)).bounds(browseX, dummyY, browseWidth, 20).build();
            row.deleteButton = Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.customMaps.remove(index); this.init(); }).bounds(deleteX, dummyY, deleteWidth, 20).build();
            uiCustomRows.add(row); dummyY += 25;
        }

        List<String> availableMaps = getCurrentlyAvailableMapNames();
        if (!availableMaps.contains(ConfigManager.configData.terrainBuilder.buildFromMap)) { ConfigManager.configData.terrainBuilder.buildFromMap = "Nothing"; }
        if (!availableMaps.contains(ConfigManager.configData.waterBuilder.buildFromMap)) { ConfigManager.configData.waterBuilder.buildFromMap = "Nothing"; }
        if (!availableMaps.contains(ConfigManager.configData.biomeBuilder.buildFromMap)) { ConfigManager.configData.biomeBuilder.buildFromMap = "Nothing"; }
        if (ConfigManager.configData.structureBuilder != null && !availableMaps.contains(ConfigManager.configData.structureBuilder.buildFromMap)) { ConfigManager.configData.structureBuilder.buildFromMap = "Nothing"; }

        this.clearWidgets();
        this.uiPopupRows.clear();
        this.uiStructPopupRows.clear();

        int buttonWidth = 120;
        int startX = (this.width - (buttonWidth * 3 + 10)) / 2;
        int tabY = 20;

        this.tab1Btn = this.addRenderableWidget(Button.builder(Component.literal("1. Import Maps"), b -> switchTab(0)).bounds(startX, tabY, buttonWidth, 20).build());
        this.tab2Btn = this.addRenderableWidget(Button.builder(Component.literal("2. Color Mapping"), b -> switchTab(1)).bounds(startX + buttonWidth + 5, tabY, buttonWidth, 20).build());
        this.tab3Btn = this.addRenderableWidget(Button.builder(Component.literal("3. Custom Studio"), b -> switchTab(2)).bounds(startX + (buttonWidth * 2) + 10, tabY, buttonWidth, 20).build());

        if (activeTab == 0) {
            if (heightNameBox != null) { this.addRenderableWidget(heightNameBox); this.addRenderableWidget(heightMapBox); this.addRenderableWidget(heightBrowseBtn); this.addRenderableWidget(heightDeleteBtn); }
            if (forestNameBox != null) { this.addRenderableWidget(forestNameBox); this.addRenderableWidget(forestMapBox); this.addRenderableWidget(forestBrowseBtn); this.addRenderableWidget(forestDeleteBtn); }
            if (waterNameBox != null) { this.addRenderableWidget(waterNameBox); this.addRenderableWidget(waterMapBox); this.addRenderableWidget(waterBrowseBtn); this.addRenderableWidget(waterDeleteBtn); }
            for (CustomRowComponents row : uiCustomRows) { this.addRenderableWidget(row.nameBox); this.addRenderableWidget(row.pathBox); this.addRenderableWidget(row.browseButton); this.addRenderableWidget(row.deleteButton); }
            this.addButton = this.addRenderableWidget(Button.builder(Component.literal("+ Add New Map Layer"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.customMaps.add(new ConfigManager.CustomMap("Custom Layer", "")); this.init(); }).bounds(this.width / 2 - 75, dummyY, 150, 20).build());
        }

        if (activeTab == 1 && !isBiomePopupActive && !isStructurePopupActive && !isSlopePopupActive) {
            this.terrainMapBtn = this.addRenderableWidget(Button.builder(Component.literal("Build from: " + ConfigManager.configData.terrainBuilder.buildFromMap), b -> {
                List<String> currentMaps = getCurrentlyAvailableMapNames();
                int nIdx = (currentMaps.indexOf(ConfigManager.configData.terrainBuilder.buildFromMap) + 1) % currentMaps.size();
                ConfigManager.configData.terrainBuilder.buildFromMap = currentMaps.get(nIdx); b.setMessage(Component.literal("Build from: " + ConfigManager.configData.terrainBuilder.buildFromMap));
            }).bounds(130, 70, 190, 20).build());

            this.terrainMinYBox = this.addRenderableWidget(new EditBox(this.font, 325, 70, 35, 20, Component.literal("MinY"))); this.terrainMinYBox.setValue(String.valueOf(ConfigManager.configData.terrainBuilder.minY));
            this.terrainMaxYBox = this.addRenderableWidget(new EditBox(this.font, 365, 70, 35, 20, Component.literal("MaxY"))); this.terrainMaxYBox.setValue(String.valueOf(ConfigManager.configData.terrainBuilder.maxY));
            this.terrainSlopeBtn = this.addRenderableWidget(Button.builder(Component.literal("⚙ Configure Slope"), b -> {
                this.syncCurrentUiToConfig(); this.isSlopePopupActive = true; this.init();
            }).bounds(405, 70, 110, 20).build());

            this.waterMapBtn = this.addRenderableWidget(Button.builder(Component.literal("Build from: " + ConfigManager.configData.waterBuilder.buildFromMap), b -> {
                List<String> currentMaps = getCurrentlyAvailableMapNames();
                int nIdx = (currentMaps.indexOf(ConfigManager.configData.waterBuilder.buildFromMap) + 1) % currentMaps.size();
                ConfigManager.configData.waterBuilder.buildFromMap = currentMaps.get(nIdx); b.setMessage(Component.literal("Build from: " + ConfigManager.configData.waterBuilder.buildFromMap));
            }).bounds(130, 95, 190, 20).build());

            this.waterSeaLevelBox = this.addRenderableWidget(new EditBox(this.font, 385, 95, 50, 20, Component.literal("SeaLevel"))); this.waterSeaLevelBox.setValue(String.valueOf(ConfigManager.configData.waterBuilder.seaLevel));

            this.biomeMapBtn = this.addRenderableWidget(Button.builder(Component.literal("Build from: " + ConfigManager.configData.biomeBuilder.buildFromMap), b -> {
                List<String> currentMaps = getCurrentlyAvailableMapNames();
                int nIdx = (currentMaps.indexOf(ConfigManager.configData.biomeBuilder.buildFromMap) + 1) % currentMaps.size();
                ConfigManager.configData.biomeBuilder.buildFromMap = currentMaps.get(nIdx); b.setMessage(Component.literal("Build from: " + ConfigManager.configData.biomeBuilder.buildFromMap));
            }).bounds(130, 120, 190, 20).build());

            this.biomeConfigureBtn = this.addRenderableWidget(Button.builder(Component.literal("⚙ Configure Matrix & Node Links"), b -> {
                this.syncCurrentUiToConfig(); this.isBiomePopupActive = true; this.popupScrollAmount = 0; this.init();
            }).bounds(325, 120, this.width - 325 - 15, 20).build());

            String structMap = ConfigManager.configData.structureBuilder != null ? ConfigManager.configData.structureBuilder.buildFromMap : "Nothing";
            this.structureMapBtn = this.addRenderableWidget(Button.builder(Component.literal("Build from: " + structMap), b -> {
                List<String> currentMaps = getCurrentlyAvailableMapNames();
                String current = ConfigManager.configData.structureBuilder != null ? ConfigManager.configData.structureBuilder.buildFromMap : "Nothing";
                int nIdx = (currentMaps.indexOf(current) + 1) % currentMaps.size();
                String nextMap = currentMaps.get(nIdx);
                if (ConfigManager.configData.structureBuilder != null) ConfigManager.configData.structureBuilder.buildFromMap = nextMap;
                b.setMessage(Component.literal("Build from: " + nextMap));
            }).bounds(130, 145, 190, 20).build());

            this.structConfigureBtn = this.addRenderableWidget(Button.builder(Component.literal("⚙ Configure Hex & Structures"), b -> {
                this.syncCurrentUiToConfig(); this.isStructurePopupActive = true; this.popupScrollAmount = 0; this.init();
            }).bounds(325, 145, this.width - 325 - 15, 20).build());

            this.undergroundAvgSurfaceYBox = this.addRenderableWidget(new EditBox(this.font, 230, 170, 50, 20, Component.literal("Avg Surface Y")));
            if (ConfigManager.configData.undergroundBuilder != null) {
                this.undergroundAvgSurfaceYBox.setValue(String.valueOf(ConfigManager.configData.undergroundBuilder.averageSurfaceY));
            } else {
                this.undergroundAvgSurfaceYBox.setValue("1000");
            }

            this.summerFreezeYBox = this.addRenderableWidget(new EditBox(this.font, 210, 220, 45, 20, Component.literal("Summer Y")));
            this.winterFreezeYBox = this.addRenderableWidget(new EditBox(this.font, 360, 220, 45, 20, Component.literal("Winter Y")));
            this.springAutumnFreezeYBox = this.addRenderableWidget(new EditBox(this.font, 550, 220, 45, 20, Component.literal("Spring/Autumn Y")));

            if (ConfigManager.configData.seasonBuilder != null) {
                this.summerFreezeYBox.setValue(String.valueOf(ConfigManager.configData.seasonBuilder.summerFreezeY));
                this.winterFreezeYBox.setValue(String.valueOf(ConfigManager.configData.seasonBuilder.winterFreezeY));
                this.springAutumnFreezeYBox.setValue(String.valueOf(ConfigManager.configData.seasonBuilder.springAutumnFreezeY));
            } else {
                this.summerFreezeYBox.setValue("3000"); this.winterFreezeYBox.setValue("200"); this.springAutumnFreezeYBox.setValue("1300");
            }
            boolean cavesEnabled = ConfigManager.configData.undergroundBuilder != null && ConfigManager.configData.undergroundBuilder.generateCaves;
            this.caveToggleBtn = this.addRenderableWidget(Button.builder(Component.literal("Cave Generator: " + (cavesEnabled ? "§aON" : "§cOFF")), b -> {
                if (ConfigManager.configData.undergroundBuilder != null) {
                    ConfigManager.configData.undergroundBuilder.generateCaves = !ConfigManager.configData.undergroundBuilder.generateCaves;
                    b.setMessage(Component.literal("Cave Generator: " + (ConfigManager.configData.undergroundBuilder.generateCaves ? "§aON" : "§cOFF")));
                }
            }).bounds(130, 245, 190, 20).build());
        }
        if (isBiomePopupActive && activeTab == 1) {
            this.savePresetBtn = this.addRenderableWidget(Button.builder(Component.literal("💾 Create / Save Preset"), b -> { syncCurrentUiToConfig(); exportPresetDialog(false); }).bounds(15, 48, 140, 20).build());
            this.loadPresetBtn = this.addRenderableWidget(Button.builder(Component.literal("📂 Load Preset File"), b -> { importPresetDialog(false); }).bounds(160, 48, 120, 20).build());

            int pY = 88;
            for (int i = 0; i < ConfigManager.configData.biomeBuilder.rules.size(); i++) {
                final int idx = i; ConfigManager.BiomeRule rule = ConfigManager.configData.biomeBuilder.rules.get(idx); PopupInnerRow row = new PopupInnerRow();
                row.typeBtn = this.addRenderableWidget(Button.builder(Component.literal(rule.ruleType.equals("MATRIX") ? "Bit Link" : "HEX Link"), b -> { syncCurrentUiToConfig(); rule.ruleType = rule.ruleType.equals("MATRIX") ? "COLOR" : "MATRIX"; this.init(); }).bounds(15, pY, 65, 20).build());
                row.minYBox = this.addRenderableWidget(new EditBox(this.font, 85, pY, 35, 20, Component.literal("MinY"))); row.minYBox.setValue(String.valueOf(rule.minY));
                row.maxYBox = this.addRenderableWidget(new EditBox(this.font, 125, pY, 35, 20, Component.literal("MaxY"))); row.maxYBox.setValue(String.valueOf(rule.maxY));

                if (rule.ruleType.equals("MATRIX")) {
                    row.forestValueBox = this.addRenderableWidget(new EditBox(this.font, 165, pY, 60, 20, Component.literal("Bit"))); row.forestValueBox.setValue(String.valueOf(rule.forestValue));
                } else {
                    row.hexBox = this.addRenderableWidget(new EditBox(this.font, 165, pY, 60, 20, Component.literal("#HEX"))); row.hexBox.setValue(rule.hexColor); row.hexBox.setMaxLength(7);
                }

                row.biomeBox = this.addRenderableWidget(new EditBox(this.font, 235, pY, this.width - 275, 20, Component.literal("Biome ID"))); if (row.biomeBox != null) row.biomeBox.setValue(rule.targetBiome);
                row.deleteButton = this.addRenderableWidget(Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.biomeBuilder.rules.remove(idx); this.init(); }).bounds(this.width - 35, pY, 20, 20).build());
                uiPopupRows.add(row); pY += 25;
            }

            this.popupAddBtn = this.addRenderableWidget(Button.builder(Component.literal("+ Add Node Entry"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.biomeBuilder.rules.add(new ConfigManager.BiomeRule()); this.init(); }).bounds(this.width / 2 - 110, pY, 100, 20).build());
            this.popupCloseBtn = this.addRenderableWidget(Button.builder(Component.literal("§aApply & Close"), b -> { syncCurrentUiToConfig(); this.isBiomePopupActive = false; this.init(); }).bounds(this.width / 2, pY, 100, 20).build());
            this.backButton = this.addRenderableWidget(Button.builder(Component.literal("← Cancel / Back"), b -> { this.isBiomePopupActive = false; this.init(); }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }

        if (isSlopePopupActive && activeTab == 1) {
            int pY = 88;
            int labelX = 15;
            int slopeBoxX = 150;
            int slopeBoxWidth = this.width - slopeBoxX - 35;

            this.slopeBaseThresholdBox = this.addRenderableWidget(new EditBox(this.font, slopeBoxX, pY, 100, 20, Component.literal("Threshold")));
            this.slopeBaseThresholdBox.setValue(String.valueOf(ConfigManager.configData.terrainBuilder.baseSlopeThreshold));
            pY += 25;

            this.slopeYFactorBox = this.addRenderableWidget(new EditBox(this.font, slopeBoxX, pY, 100, 20, Component.literal("Y Factor")));
            this.slopeYFactorBox.setValue(String.valueOf(ConfigManager.configData.terrainBuilder.slopeThresholdYFactor));
            pY += 25;

            this.slopeBlockBox = this.addRenderableWidget(new EditBox(this.font, slopeBoxX, pY, slopeBoxWidth, 20, Component.literal("Block ID")));
            this.slopeBlockBox.setValue(ConfigManager.configData.terrainBuilder.slopeBlock);
            this.slopeBlockBox.setMaxLength(64);
            pY += 25;

            this.slopeUnderBlockBox = this.addRenderableWidget(new EditBox(this.font, slopeBoxX, pY, slopeBoxWidth, 20, Component.literal("Under Block ID")));
            this.slopeUnderBlockBox.setValue(ConfigManager.configData.terrainBuilder.slopeUnderBlock);
            this.slopeUnderBlockBox.setMaxLength(64);
            pY += 30;

            this.slopeCloseBtn = this.addRenderableWidget(Button.builder(Component.literal("§aApply & Close"), b -> {
                try { ConfigManager.configData.terrainBuilder.baseSlopeThreshold = Integer.parseInt(slopeBaseThresholdBox.getValue()); } catch (Exception e) {}
                try { ConfigManager.configData.terrainBuilder.slopeThresholdYFactor = Double.parseDouble(slopeYFactorBox.getValue()); } catch (Exception e) {}
                ConfigManager.configData.terrainBuilder.slopeBlock = slopeBlockBox.getValue();
                ConfigManager.configData.terrainBuilder.slopeUnderBlock = slopeUnderBlockBox.getValue();
                this.isSlopePopupActive = false; this.init();
            }).bounds(this.width / 2, pY, 100, 20).build());

            this.slopeBackBtn = this.addRenderableWidget(Button.builder(Component.literal("← Cancel / Back"), b -> { this.isSlopePopupActive = false; this.init(); }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }

        this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            syncCurrentUiToConfig(); ConfigManager.save(); this.onClose();
        }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());


        if (isStructurePopupActive && activeTab == 1) {
            this.savePresetBtn = this.addRenderableWidget(Button.builder(Component.literal("💾 Create / Save Preset"), b -> { syncCurrentUiToConfig(); exportPresetDialog(true); }).bounds(15, 48, 140, 20).build());
            this.loadPresetBtn = this.addRenderableWidget(Button.builder(Component.literal("📂 Load Preset File"), b -> { importPresetDialog(true); }).bounds(160, 48, 120, 20).build());

            int pY = 88;
            for (int i = 0; i < ConfigManager.configData.structureBuilder.rules.size(); i++) {
                final int idx = i; ConfigManager.StructureRule rule = ConfigManager.configData.structureBuilder.rules.get(idx); StructPopupRow row = new StructPopupRow();
                row.hexBox = this.addRenderableWidget(new EditBox(this.font, 15, pY, 60, 20, Component.literal("#HEX"))); row.hexBox.setValue(rule.hexColor); row.hexBox.setMaxLength(7);
                row.structBox = this.addRenderableWidget(new EditBox(this.font, 85, pY, this.width - 125, 20, Component.literal("Structure ID"))); if(row.structBox != null) row.structBox.setValue(rule.targetStructure);
                row.deleteButton = this.addRenderableWidget(Button.builder(Component.literal("§cX"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.structureBuilder.rules.remove(idx); this.init(); }).bounds(this.width - 35, pY, 20, 20).build());
                uiStructPopupRows.add(row); pY += 25;
            }
            this.structPopupAddBtn = this.addRenderableWidget(Button.builder(Component.literal("+ Add Structure"), b -> { syncCurrentUiToConfig(); ConfigManager.configData.structureBuilder.rules.add(new ConfigManager.StructureRule()); this.init(); }).bounds(this.width / 2 - 110, pY, 100, 20).build());
            this.structPopupCloseBtn = this.addRenderableWidget(Button.builder(Component.literal("§aApply & Close"), b -> { syncCurrentUiToConfig(); this.isStructurePopupActive = false; this.init(); }).bounds(this.width / 2, pY, 100, 20).build());
            this.structBackButton = this.addRenderableWidget(Button.builder(Component.literal("← Cancel / Back"), b -> { this.isStructurePopupActive = false; this.init(); }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }

        if (isBiomePopupActive || isStructurePopupActive || isSlopePopupActive) {
            tab1Btn.visible = false; tab2Btn.visible = false; tab3Btn.visible = false; doneButton.visible = false;
        }

    }

    private void switchTab(int tabIndex) {
        this.syncCurrentUiToConfig(); this.activeTab = tabIndex; this.scrollAmount = 0; this.isBiomePopupActive = false; this.isStructurePopupActive = false; this.isSlopePopupActive = false; this.init();
    }

    private void syncCurrentUiToConfig() {
        if (activeTab == 0) {
            if (ConfigManager.configData.showHeightmap && this.heightNameBox != null) { ConfigManager.configData.heightmapName = this.heightNameBox.getValue(); ConfigManager.configData.heightmapPath = this.heightMapBox.getValue(); }
            if (ConfigManager.configData.showForestmap && this.forestNameBox != null) { ConfigManager.configData.forestmapName = this.forestNameBox.getValue(); ConfigManager.configData.forestmapPath = this.forestMapBox.getValue(); }
            if (ConfigManager.configData.showWatermap && this.waterNameBox != null) { ConfigManager.configData.watermapName = this.waterNameBox.getValue(); ConfigManager.configData.watermapPath = this.waterMapBox.getValue(); }
            List<ConfigManager.CustomMap> updatedList = new ArrayList<>();
            for (CustomRowComponents row : uiCustomRows) { updatedList.add(new ConfigManager.CustomMap(row.nameBox.getValue(), row.pathBox.getValue())); }
            ConfigManager.configData.customMaps = updatedList;
        }

        if (activeTab == 1 && !isBiomePopupActive && !isStructurePopupActive && this.terrainMinYBox != null) {
            try {
                ConfigManager.configData.terrainBuilder.minY = Integer.parseInt(terrainMinYBox.getValue());

                int parsedMaxY = Integer.parseInt(terrainMaxYBox.getValue());
                ConfigManager.configData.terrainBuilder.maxY = Math.min(4000, parsedMaxY);
                terrainMaxYBox.setValue(String.valueOf(ConfigManager.configData.terrainBuilder.maxY));

                ConfigManager.configData.waterBuilder.seaLevel = Integer.parseInt(waterSeaLevelBox.getValue());

                if (ConfigManager.configData.undergroundBuilder != null && this.undergroundAvgSurfaceYBox != null) {
                    ConfigManager.configData.undergroundBuilder.averageSurfaceY = Integer.parseInt(undergroundAvgSurfaceYBox.getValue());
                }

                if (ConfigManager.configData.seasonBuilder != null && this.summerFreezeYBox != null && this.winterFreezeYBox != null) {
                    ConfigManager.configData.seasonBuilder.summerFreezeY = Integer.parseInt(summerFreezeYBox.getValue());
                    ConfigManager.configData.seasonBuilder.winterFreezeY = Integer.parseInt(winterFreezeYBox.getValue());
                    ConfigManager.configData.seasonBuilder.springAutumnFreezeY = Integer.parseInt(springAutumnFreezeYBox.getValue());
                }
            } catch (NumberFormatException ignored) {}
        }

        if (isBiomePopupActive && !uiPopupRows.isEmpty()) {
            int maxSafeLimit = Math.min(uiPopupRows.size(), ConfigManager.configData.biomeBuilder.rules.size());
            for (int i = 0; i < maxSafeLimit; i++) {
                PopupInnerRow uiRow = uiPopupRows.get(i); ConfigManager.BiomeRule rule = ConfigManager.configData.biomeBuilder.rules.get(i);
                try {
                    if (uiRow.minYBox != null) rule.minY = Integer.parseInt(uiRow.minYBox.getValue());
                    if (uiRow.maxYBox != null) rule.maxY = Integer.parseInt(uiRow.maxYBox.getValue());
                } catch (NumberFormatException ignored) {}
                if (rule.ruleType.equals("MATRIX")) {
                    try { if (uiRow.forestValueBox != null) rule.forestValue = Integer.parseInt(uiRow.forestValueBox.getValue()); } catch (NumberFormatException ignored) {}
                } else { if (uiRow.hexBox != null) rule.hexColor = uiRow.hexBox.getValue(); }
                if (uiRow.biomeBox != null) rule.targetBiome = uiRow.biomeBox.getValue();
            }
        }

        if (isStructurePopupActive && !uiStructPopupRows.isEmpty()) {
            int maxSafeLimit = Math.min(uiStructPopupRows.size(), ConfigManager.configData.structureBuilder.rules.size());
            for (int i = 0; i < maxSafeLimit; i++) {
                StructPopupRow uiRow = uiStructPopupRows.get(i); ConfigManager.StructureRule rule = ConfigManager.configData.structureBuilder.rules.get(i);
                if (uiRow.hexBox != null) rule.hexColor = uiRow.hexBox.getValue();
                if (uiRow.structBox != null) rule.targetStructure = uiRow.structBox.getValue();
            }
        }

        if (isSlopePopupActive) {
            try { ConfigManager.configData.terrainBuilder.baseSlopeThreshold = Integer.parseInt(slopeBaseThresholdBox.getValue()); } catch (Exception e) {}
            try { ConfigManager.configData.terrainBuilder.slopeThresholdYFactor = Double.parseDouble(slopeYFactorBox.getValue()); } catch (Exception e) {}
            if (slopeBlockBox != null) ConfigManager.configData.terrainBuilder.slopeBlock = slopeBlockBox.getValue();
            if (slopeUnderBlockBox != null) ConfigManager.configData.terrainBuilder.slopeUnderBlock = slopeUnderBlockBox.getValue();
        }
    }

    private void exportPresetDialog(boolean isStructure) {
        long windowHandle = Minecraft.getInstance().getWindow().getWindow(); boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        CompletableFuture.runAsync(() -> {
            try {
                String result = TinyFileDialogs.tinyfd_saveFileDialog(
                        "Create / Export Custom Preset",
                        System.getProperty("user.home") + File.separator + (isStructure ? "my_structure_preset.json" : "my_biome_preset.json"),
                        null, "JSON Presets (*.json)");

                if (result != null) {
                    File file = new File(result);
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    String json = isStructure ? gson.toJson(ConfigManager.configData.structureBuilder.rules) : gson.toJson(ConfigManager.configData.biomeBuilder.rules);
                    java.nio.file.Files.writeString(file.toPath(), json);
                }

                Minecraft.getInstance().execute(() -> {
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);
                    if (isWindows) {
                        try {
                            long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(windowHandle);
                            org.lwjgl.system.SharedLibrary user32 = org.lwjgl.system.Library.loadNative("org.lwjgl", "user32");
                            long showWindowAddr = user32.getFunctionAddress("ShowWindow"); long setForegroundWindowAddr = user32.getFunctionAddress("SetForegroundWindow");
                            org.lwjgl.system.JNI.callPI(hwnd, 9, showWindowAddr); org.lwjgl.system.JNI.callPI(hwnd, setForegroundWindowAddr); user32.free();
                        } catch (Exception ignored) {}
                    }
                    org.lwjgl.glfw.GLFW.glfwRestoreWindow(windowHandle); org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void importPresetDialog(boolean isStructure) {
        long windowHandle = Minecraft.getInstance().getWindow().getWindow(); boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        CompletableFuture.runAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1); filters.put(stack.UTF8("*.json")); filters.flip();
                String result = TinyFileDialogs.tinyfd_openFileDialog("Select Custom Preset File", System.getProperty("user.home") + File.separator, filters, "JSON Presets (*.json)", false);
                boolean rulesLoaded = false;
                if (result != null) {
                    File file = new File(result); String json = java.nio.file.Files.readString(file.toPath());
                    com.google.gson.Gson gson = new com.google.gson.Gson();

                    if (isStructure) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<ConfigManager.StructureRule>>(){}.getType();
                        ArrayList<ConfigManager.StructureRule> loadedRules = gson.fromJson(json, listType);
                        if (loadedRules != null) { ConfigManager.configData.structureBuilder.rules = loadedRules; rulesLoaded = true; }
                    } else {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<ConfigManager.BiomeRule>>(){}.getType();
                        ArrayList<ConfigManager.BiomeRule> loadedRules = gson.fromJson(json, listType);
                        if (loadedRules != null) { ConfigManager.configData.biomeBuilder.rules = loadedRules; rulesLoaded = true; }
                    }
                }
                final boolean shouldRefreshUi = rulesLoaded;
                Minecraft.getInstance().execute(() -> {
                    if (shouldRefreshUi) this.init();
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);
                    if (isWindows) {
                        try {
                            long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(windowHandle);
                            org.lwjgl.system.SharedLibrary user32 = org.lwjgl.system.Library.loadNative("org.lwjgl", "user32");
                            long showWindowAddr = user32.getFunctionAddress("ShowWindow"); long setForegroundWindowAddr = user32.getFunctionAddress("SetForegroundWindow");
                            org.lwjgl.system.JNI.callPI(hwnd, 9, showWindowAddr); org.lwjgl.system.JNI.callPI(hwnd, setForegroundWindowAddr); user32.free();
                        } catch (Exception ignored) {}
                    }
                    org.lwjgl.glfw.GLFW.glfwRestoreWindow(windowHandle); org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isBiomePopupActive || isStructurePopupActive || isSlopePopupActive) this.popupScrollAmount = this.popupScrollAmount - amount * 18;
        else this.scrollAmount = this.scrollAmount - amount * 18;
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(guiGraphics);
        int panelTop = 48; int panelBottom = this.height - 35;

        if (isSlopePopupActive) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xD0000000);
            guiGraphics.fill(10, 30, this.width - 10, this.height - 35, 0xFF222222);

            guiGraphics.drawCenteredString(this.font, "§eSlope Configuration", this.width / 2, 35, 0xFFFFFF);

            guiGraphics.drawString(this.font, "§7Base Threshold (1-10):", 15, 93, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Height Factor (0.0 - 1.0):", 15, 118, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Slope Block ID:", 15, 143, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Under Block ID:", 15, 168, 0xAAAAAA);

            if (this.slopeBaseThresholdBox != null) this.slopeBaseThresholdBox.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.slopeYFactorBox != null) this.slopeYFactorBox.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.slopeBlockBox != null) this.slopeBlockBox.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.slopeUnderBlockBox != null) this.slopeUnderBlockBox.render(guiGraphics, mouseX, mouseY, partialTick);

            if (this.slopeCloseBtn != null) this.slopeCloseBtn.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.slopeBackBtn != null) {
                this.slopeBackBtn.setY(this.height - 30);
                this.slopeBackBtn.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }

        if (isStructurePopupActive) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xD0000000);
            guiGraphics.fill(10, 30, this.width - 10, this.height - 35, 0xFF222222);
            guiGraphics.drawString(this.font, "§7#HEX", 15, 75, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Target Structure ID", 85, 75, 0xAAAAAA);

            if (this.savePresetBtn != null) this.savePresetBtn.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.loadPresetBtn != null) this.loadPresetBtn.render(guiGraphics, mouseX, mouseY, partialTick);

            int maxPScroll = Math.max(0, 88 + (uiStructPopupRows.size() * 25) + 55 - (panelBottom - 35) + 15);
            this.popupScrollAmount = Math.max(0, Math.min(this.popupScrollAmount, maxPScroll));
            int currentY = 88 - (int) popupScrollAmount;

            for (StructPopupRow row : uiStructPopupRows) {
                row.hexBox.setY(currentY); row.structBox.setY(currentY); row.deleteButton.setY(currentY);
                boolean v = currentY >= 86 && currentY <= (panelBottom - 25); row.hexBox.visible = v; row.structBox.visible = v; row.deleteButton.visible = v;
                currentY += 25;
            }
            if(structPopupAddBtn != null) { structPopupAddBtn.setY(currentY); structPopupAddBtn.visible = currentY >= 86 && currentY <= (panelBottom - 25); }
            if(structPopupCloseBtn != null) { structPopupCloseBtn.setY(currentY); structPopupCloseBtn.visible = currentY >= 86 && currentY <= (panelBottom - 25); }

            guiGraphics.enableScissor(12, 86, this.width - 12, panelBottom - 25);
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.Renderable r && r != tab1Btn && r != tab2Btn && r != tab3Btn && r != doneButton && r != structBackButton && r != savePresetBtn && r != loadPresetBtn) {
                    r.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
            guiGraphics.disableScissor();

            guiGraphics.drawCenteredString(this.font, "§eStructure Configuration: §b" + ConfigManager.configData.structureBuilder.buildFromMap, this.width / 2, 35, 0xFFFFFF);
            if (this.structBackButton != null) { this.structBackButton.setY(this.height - 30); this.structBackButton.render(guiGraphics, mouseX, mouseY, partialTick); }
            return;
        }

        if (isBiomePopupActive) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xD0000000);
            guiGraphics.fill(10, 30, this.width - 10, this.height - 35, 0xFF222222);

            guiGraphics.drawString(this.font, "§7Mode", 15, 75, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Y Range", 85, 75, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Bit / #HEX", 165, 75, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Target Biome ID", 235, 75, 0xAAAAAA);

            if (this.savePresetBtn != null) this.savePresetBtn.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.loadPresetBtn != null) this.loadPresetBtn.render(guiGraphics, mouseX, mouseY, partialTick);

            int maxPScroll = Math.max(0, 88 + (uiPopupRows.size() * 25) + 55 - (panelBottom - 35) + 15);
            this.popupScrollAmount = Math.max(0, Math.min(this.popupScrollAmount, maxPScroll));
            int currentY = 88 - (int) popupScrollAmount;

            for (PopupInnerRow row : uiPopupRows) {
                row.typeBtn.setY(currentY); row.biomeBox.setY(currentY); row.deleteButton.setY(currentY);
                boolean v = currentY >= 86 && currentY <= (panelBottom - 25); row.typeBtn.visible = v; row.biomeBox.visible = v; row.deleteButton.visible = v;
                if (row.minYBox != null) { row.minYBox.setY(currentY); row.minYBox.visible = v; }
                if (row.maxYBox != null) { row.maxYBox.setY(currentY); row.maxYBox.visible = v; }
                if (row.forestValueBox != null) { row.forestValueBox.setY(currentY); row.forestValueBox.visible = v; }
                if (row.hexBox != null) { row.hexBox.setY(currentY); row.hexBox.visible = v; }
                currentY += 25;
            }

            if(popupAddBtn != null) { popupAddBtn.setY(currentY); popupAddBtn.visible = currentY >= 86 && currentY <= (panelBottom - 25); }
            if(popupCloseBtn != null) { popupCloseBtn.setY(currentY); popupCloseBtn.visible = currentY >= 86 && currentY <= (panelBottom - 25); }

            guiGraphics.enableScissor(12, 86, this.width - 12, panelBottom - 25);
            for (var child : this.children()) { if (child instanceof net.minecraft.client.gui.components.Renderable r && r != tab1Btn && r != tab2Btn && r != tab3Btn && r != doneButton && r != backButton && r != savePresetBtn && r != loadPresetBtn) { r.render(guiGraphics, mouseX, mouseY, partialTick); } }
            guiGraphics.disableScissor();

            guiGraphics.drawCenteredString(this.font, "§eNode Configuration: §b" + ConfigManager.configData.biomeBuilder.buildFromMap, this.width / 2, 35, 0xFFFFFF);
            if (this.backButton != null) { this.backButton.setY(this.height - 30); this.backButton.render(guiGraphics, mouseX, mouseY, partialTick); }
            return;
        }

        guiGraphics.fill(0, panelTop, this.width, panelBottom, 0x99000000);

        int rowCount = activeTab == 0 ? uiCustomRows.size() + 3 : 3;
        int maxScroll = Math.max(0, 70 + (rowCount * 25) + 55 - (panelBottom - panelTop) + 15);
        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, maxScroll));
        int currentY = 70 - (int) scrollAmount;

        if (activeTab == 0) {
            if (heightNameBox != null) { updateRow1(heightNameBox, heightMapBox, heightBrowseBtn, heightDeleteBtn, currentY, panelTop, panelBottom); currentY += 25; }
            if (forestNameBox != null) { updateRow1(forestNameBox, forestMapBox, forestBrowseBtn, forestDeleteBtn, currentY, panelTop, panelBottom); currentY += 25; }
            if (waterNameBox != null) { updateRow1(waterNameBox, waterMapBox, waterBrowseBtn, waterDeleteBtn, currentY, panelTop, panelBottom); currentY += 25; }
            for (CustomRowComponents row : uiCustomRows) { updateRow1(row.nameBox, row.pathBox, row.browseButton, row.deleteButton, currentY, panelTop, panelBottom); currentY += 25; }
            if (this.addButton != null) { this.addButton.setY(currentY); this.addButton.visible = currentY >= (panelTop - 5) && currentY <= (panelBottom - 20); }
        }

        if (activeTab == 1) {
            guiGraphics.drawString(this.font, "🛠 TIF BUILDERS", 15, 56, 0x66AAFF);
            guiGraphics.drawString(this.font, "🏔 Terrain Builder:", 15, 76, 0xFFFFFF);
            guiGraphics.drawString(this.font, "§7Min Y", 325, 58, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Max Y", 365, 58, 0xAAAAAA);
            guiGraphics.drawString(this.font, "§7Slope", 405, 58, 0xAAAAAA);
            guiGraphics.drawString(this.font, "🌊 Water Builder:", 15, 101, 0xFFFFFF);
            guiGraphics.drawString(this.font, "Sea Level:", 325, 101, 0xAAAAAA);
            guiGraphics.drawString(this.font, "🌳 Biome Builder:", 15, 126, 0xFFFFFF);
            guiGraphics.drawString(this.font, "🏘 Structure Builder:", 15, 151, 0xFFFFFF);

            guiGraphics.drawString(this.font, "🕳 Underground Builder:", 15, 175, 0xFFFFFF);
            guiGraphics.drawString(this.font, "Avg Surface Y:", 140, 175, 0xAAAAAA);

            int dividerY = 200;
            guiGraphics.fill(15, dividerY, this.width - 15, dividerY + 1, 0x55FFFFFF);
            guiGraphics.drawString(this.font, "⚙ SYSTEM MANAGERS", 15, dividerY + 6, 0x66AAFF);
            guiGraphics.drawString(this.font, "❄ Season Manager:", 15, 225, 0xFFFFFF);
            guiGraphics.drawString(this.font, "🕳 Cave Generator:", 15, 250, 0xFFFFFF);
            guiGraphics.drawString(this.font, "Summer Freeze Y:", 115, 225, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Winter Freeze Y:", 270, 225, 0xAAAAAA);
            guiGraphics.drawString(this.font, "Spring/Autumn Freeze Y:", 420, 225, 0xAAAAAA);
        }

        guiGraphics.enableScissor(0, panelTop + 2, this.width, panelBottom - 2);
        for (var child : this.children()) { if (child instanceof net.minecraft.client.gui.components.Renderable r && r != tab1Btn && r != tab2Btn && r != tab3Btn && r != doneButton) r.render(guiGraphics, mouseX, mouseY, partialTick); }
        guiGraphics.disableScissor();

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 5, 0xFFFFFF);
        this.tab1Btn.render(guiGraphics, mouseX, mouseY, partialTick); this.tab2Btn.render(guiGraphics, mouseX, mouseY, partialTick); this.tab3Btn.render(guiGraphics, mouseX, mouseY, partialTick); this.doneButton.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTabContent(guiGraphics);
    }

    private void updateRow1(EditBox name, EditBox path, Button b, Button d, int y, int t, int bot) {
        name.setY(y); path.setY(y); b.setY(y); if (d != null) d.setY(y);
        boolean v = y >= (t - 5) && y <= (bot - 20); name.visible = v; path.visible = v; b.visible = v; if (d != null) d.visible = v;
    }

    private void renderTabContent(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (activeTab == 0) {
            guiGraphics.drawCenteredString(this.font, "§7TIFF Source Configuration (Scroll with Mouse Wheel)", centerX, 52, 0xAAAAAA);
        } else if (activeTab == 2) {
            guiGraphics.drawCenteredString(this.font, "§7Custom Object & Tree Factory", centerX, 52, 0xAAAAAA);
            guiGraphics.drawCenteredString(this.font, "§e⏳ Coming Soon...", centerX, centerY - 15, 0xFFFF55);
            guiGraphics.drawCenteredString(this.font, "§7This feature is currently under development.", centerX, centerY + 5, 0xAAAAAA);
        }
    }

    private void openFilePicker(EditBox targetBox) {
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        CompletableFuture.runAsync(() -> {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.tif"));
                filters.flip();

                String result = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        "Select TIF Map File",
                        System.getProperty("user.home") + java.io.File.separator,
                        filters,
                        "TIF Images (*.tif)",
                        false
                );

                Minecraft.getInstance().execute(() -> {
                    if (result != null) {
                        targetBox.setValue(new java.io.File(result).getAbsolutePath());
                    }

                    org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);

                    if (isWindows) {
                        try {
                            long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(windowHandle);
                            org.lwjgl.system.SharedLibrary user32 = org.lwjgl.system.Library.loadNative("org.lwjgl", "user32");
                            long showWindowAddr = user32.getFunctionAddress("ShowWindow");
                            long setForegroundWindowAddr = user32.getFunctionAddress("SetForegroundWindow");

                            org.lwjgl.system.JNI.callPI(hwnd, 9, showWindowAddr);
                            org.lwjgl.system.JNI.callPI(hwnd, setForegroundWindowAddr);
                            user32.free();
                        } catch (Exception ignored) {}
                    }

                    org.lwjgl.glfw.GLFW.glfwRestoreWindow(windowHandle);
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(windowHandle);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override public void onClose() { if (this.minecraft != null) this.minecraft.setScreen(this.lastScreen); }
    @Override public boolean isPauseScreen() { return true; }
}