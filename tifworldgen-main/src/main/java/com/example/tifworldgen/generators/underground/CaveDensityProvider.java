package com.example.tifworldgen.generators.underground;

public interface CaveDensityProvider {
    double computeDensity(int x, int y, int z, int depth, double simY, double waterProximity);
}