package com.example.tifworldgen.generators;

public interface LayerGenerator {
    void processColumn(int worldX, int worldZ, ColumnData columnData);
}