package com.tifworldgen.lib;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;

@Mod("tifworldgenlib")
public class TifWorldGenLib {
    public static final String MOD_ID = "tifworldgenlib";

    public TifWorldGenLib() {
        bootstrapDirectories(FMLPaths.GAMEDIR.get().toFile());
        System.out.println("[WMAPGEN-LIB] TifWorldGenLib ready (pure Java GeoTIFF reader, no native libraries).");
    }

    private static void bootstrapDirectories(File gameDir) {
        new File(gameDir, "tifworldgen/maps").mkdirs();
    }

    public static String resolveMapPath(String path) {
        if (path == null || path.isEmpty()) return path;
        File f = new File(path);
        if (f.isAbsolute()) return path;
        return FMLPaths.GAMEDIR.get().resolve(path).toFile().getAbsolutePath();
    }
}
