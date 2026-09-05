package com.example.tifworldgen.layers.utils;

public class LayerSecurity {
    public static boolean isAuthorizedCaller() {
        String threadName = Thread.currentThread().getName();

        return threadName.contains("Server thread") ||
                threadName.contains("WMap-") ||
                threadName.contains("Tiff-") ||
                threadName.contains("Worker");
    }
}