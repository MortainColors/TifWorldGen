package com.example.tifworldgen.generators;

import com.example.tifworldgen.config.ConfigManager;
import com.example.tifworldgen.layers.StructureLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

public class StructureLayerGenerator {

    private final StructureLayer structureLayer;

    public StructureLayerGenerator(StructureLayer structureLayer, List<ConfigManager.StructureRule> rules) {
        this.structureLayer = structureLayer;
    }

    public void filterStructures(ChunkAccess chunk) {
        if (this.structureLayer == null) return;

        String mapName = this.structureLayer.getMapName();
        int blockX = chunk.getPos().getMiddleBlockX();
        int blockZ = chunk.getPos().getMiddleBlockZ();

        if ("Black Map".equalsIgnoreCase(mapName)) {
            deleteSurfaceStructures(chunk, null);
            return;
        }

        List<ConfigManager.StructureRule> rules = ConfigManager.configData != null
                && ConfigManager.configData.structureBuilder != null
                && ConfigManager.configData.structureBuilder.rules != null
                ? ConfigManager.configData.structureBuilder.rules
                : List.of();
        boolean hasRules = !rules.isEmpty();

        if (("Nothing".equalsIgnoreCase(mapName) || "White Map".equalsIgnoreCase(mapName)) && !hasRules) {
            return;
        }

        String currentHex = this.structureLayer.getHexColor(blockX, blockZ);

        for (var entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            var key = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
            if (key == null) continue;

            String structureName = key.toString();

            if (structureName.contains("ancient_city") || structureName.contains("mineshaft") ||
                    structureName.contains("stronghold") || structureName.contains("fortress")) {
                continue;
            }

            ConfigManager.StructureRule matchingRule = null;
            if (hasRules) {
                for (ConfigManager.StructureRule rule : rules) {
                    if (rule != null && structureName.equals(rule.targetStructure)) {
                        matchingRule = rule;
                        break;
                    }
                }
            }

            if (matchingRule != null) {
                if (!currentHex.equalsIgnoreCase(matchingRule.hexColor)) {
                    chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                }
            } else {
                if (hasRules) {
                    chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                }
            }
        }
    }

    private void deleteSurfaceStructures(ChunkAccess chunk, String exception) {
        for (var entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            var key = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
            if (key == null) continue;

            String name = key.toString();
            if (name.contains("ancient_city") || name.contains("mineshaft") || name.contains("stronghold") || name.contains("fortress")) {
                continue;
            }
            if (exception != null && name.equals(exception)) continue;

            chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        }
    }
}