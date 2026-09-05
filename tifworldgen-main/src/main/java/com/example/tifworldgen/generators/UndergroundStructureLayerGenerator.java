package com.example.tifworldgen.generators;

import com.example.tifworldgen.layers.StructureLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class UndergroundStructureLayerGenerator {

    private final StructureLayer structureLayer;

    public UndergroundStructureLayerGenerator(StructureLayer structureLayer) {
        this.structureLayer = structureLayer;
    }




    public void filterUndergroundStructures(ChunkAccess chunk) {
        if (this.structureLayer == null || this.structureLayer.getPath() == null || this.structureLayer.getPath().isEmpty()) {
            return;
        }

        int blockX = chunk.getPos().getMiddleBlockX();
        int blockZ = chunk.getPos().getMiddleBlockZ();

        if (!this.structureLayer.isStructureAllowed(blockX, blockZ)) {

            for (var entry : chunk.getAllStarts().entrySet()) {
                Structure structure = entry.getKey();
                var key = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());

                if (key != null) {
                    String name = key.toString();

                    if (name.contains("ancient_city") || name.contains("mineshaft") || name.contains("stronghold") || name.contains("fortress")) {

                        chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                    }
                }
            }
        }
    }
}