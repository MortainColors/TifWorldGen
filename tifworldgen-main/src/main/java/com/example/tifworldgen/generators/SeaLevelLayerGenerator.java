package com.example.tifworldgen.generators;

import com.example.tifworldgen.config.ConfigManager;

public class SeaLevelLayerGenerator implements LayerGenerator {

    private int getSeaLevel() {
        if (ConfigManager.configData != null && ConfigManager.configData.waterBuilder != null) {
            return ConfigManager.configData.waterBuilder.seaLevel;
        }
        return 63;
    }

    @Override
    public void processColumn(int worldX, int worldZ, ColumnData columnData) {
        int seaLevel = getSeaLevel();

        if (columnData.finalHeight < seaLevel) {
            columnData.isWater = true;
            columnData.waterDepth = seaLevel - columnData.finalHeight;
            columnData.waterLevel = seaLevel;
            return;
        }

        if (columnData.isWater) {
            columnData.waterLevel = Math.max(columnData.finalHeight, seaLevel);
        }
    }
}