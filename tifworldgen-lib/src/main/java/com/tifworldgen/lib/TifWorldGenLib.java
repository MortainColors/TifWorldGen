package com.tifworldgen.lib;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.gdal.gdal.gdal;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Mod("tifworldgenlib")
public class TifWorldGenLib {
    public static final String MOD_ID = "tifworldgenlib";

    public TifWorldGenLib() {
        init();
    }

    private void init() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            File gameDir = FMLPaths.GAMEDIR.get().toFile();
            bootstrapDirectories(gameDir);

            if (os.contains("win")) {
                File winDir = new File(gameDir, "tifworldgen/native/Win");
                loadWindowsLibraries(winDir);
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                File linuxDir = new File(gameDir, "tifworldgen/native/Linux");
                loadLinuxLibraries(linuxDir);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + os);
            }

            gdal.AllRegister();
            int driverCount = gdal.GetDriverCount();
            System.out.println("[WMAPGEN-LIB] GDAL " + gdal.VersionInfo("--version") + " initialized successfully! Registered drivers count: " + driverCount);

        } catch (Throwable e) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Failed to load GDAL native libraries!\n");

            if (os.contains("win")) {
                errorMsg.append("• Make sure 'release-1929-x64-gdal-3-12-1-mapserver-8-6-0' is extracted at: .minecraft/tifworldgen/native/Win/\n");
                errorMsg.append("• Ensure Microsoft Visual C++ Redistributable 2015-2022 (x64) is installed on your system.\n");
            } else {
                errorMsg.append("• Make sure GDAL system package is installed (e.g. via pacman, apt, dnf) and 'libgdalalljni.so' is extracted at: .minecraft/tifworldgen/native/Linux/\n");
            }
            errorMsg.append("For more info, check the mod installation guide on the mod page.");
            System.err.println("[WMAPGEN-LIB] " + errorMsg.toString());
            throw new RuntimeException(errorMsg.toString(), e);
        }
    }





    private static void bootstrapDirectories(File gameDir) {
        File base = new File(gameDir, "tifworldgen");
        new File(base, "maps").mkdirs();
        File winDir = new File(base, "native/Win");
        File linuxDir = new File(base, "native/Linux");
        winDir.mkdirs();
        linuxDir.mkdirs();

        writeInfoFile(new File(winDir, "README-PUT_GDAL_DLL_FILES_HERE.txt"),
                "TIF WORLDGEN - GDAL native libraries for WINDOWS\n" +
                "=================================================\n" +
                "1. Download a GDAL Windows x64 build (GIS Internals, e.g. release-1929-x64-gdal-3-12-1-mapserver-8-6-0).\n" +
                "2. Extract the WHOLE downloaded release into this folder (subfolders are scanned\n" +
                "   recursively), or copy all *.dll files from its bin/ folder directly here.\n" +
                "3. It must contain gdal.dll and gdalalljni.dll plus all dependent DLLs (~40 files).\n" +
                "4. Also install Microsoft Visual C++ Redistributable 2015-2022 (x64).\n");

        writeInfoFile(new File(linuxDir, "README-PUT_LIBGDALALLJNI_SO_HERE.txt"),
                "TIF WORLDGEN - GDAL JNI bridge for LINUX\n" +
                "=========================================\n" +
                "PREREQUISITE: your distribution's GDAL package must be installed\n" +
                "(it provides the native libgdal library the bridge links against):\n" +
                "   Arch:          sudo pacman -S gdal\n" +
                "   Debian/Ubuntu: sudo apt install gdal-bin\n" +
                "   Fedora:        sudo dnf install gdal\n\n" +
                "The mod ships with a bundled libgdalalljni.so (built against GDAL 3.13.x).\n" +
                "If it matches your system GDAL version, it is extracted and used\n" +
                "automatically - nothing else to do. A copy appears in this folder as\n" +
                "libgdalalljni-bundled.so after the first launch.\n\n" +
                "If your system GDAL is a different version:\n" +
                "  Option A (Debian/Ubuntu/Fedora) - install your distribution's Java\n" +
                "  bindings instead (nothing goes into this folder):\n" +
                "     Debian/Ubuntu: sudo apt install libgdal-java\n" +
                "     Fedora:        sudo dnf install gdal-java\n" +
                "  Option B (Arch and others) - put a libgdalalljni.so built against your\n" +
                "  installed GDAL into this folder. Multiple variants can live side by side\n" +
                "  (libgdalalljni-34.so, libgdalalljni-39.so, ...); the mod automatically picks\n" +
                "  the one matching your installed system GDAL version.\n");
    }

    private static void writeInfoFile(File target, String content) {
        try {
            if (!target.exists()) {
                java.nio.file.Files.write(target.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadWindowsLibraries(File winDir) throws Exception {
        List<File> allDlls = new ArrayList<>();
        collectFiles(winDir, ".dll", allDlls);

        if (allDlls.isEmpty()) {
            throw new FileNotFoundException("No .dll files found in " + winDir.getAbsolutePath());
        }

        File gdalDll = findFile(winDir, "gdal.dll");
        File jniDll = findFile(winDir, "gdalalljni.dll");

        if (gdalDll == null || jniDll == null) {
            throw new FileNotFoundException("Could not find gdal.dll or gdalalljni.dll in " + winDir.getAbsolutePath());
        }

        List<File> pending = new ArrayList<>(allDlls);
        pending.remove(gdalDll);
        pending.remove(jniDll);

        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            List<File> loadedThisPass = new ArrayList<>();
            for (File dll : pending) {
                try {
                    System.load(dll.getAbsolutePath());
                    loadedThisPass.add(dll);
                    progress = true;
                } catch (Throwable ignored) {
                }
            }
            pending.removeAll(loadedThisPass);
        }

        System.load(gdalDll.getAbsolutePath());
        System.load(jniDll.getAbsolutePath());

        System.out.println("[WMAPGEN-LIB] Windows GDAL libraries successfully loaded from: " + gdalDll.getParent());
    }

    private static void loadLinuxLibraries(File linuxDir) {
        if (linuxDir.exists() && linuxDir.isDirectory()) {
            List<File> allSoFiles = new ArrayList<>();
            collectFiles(linuxDir, ".so", allSoFiles);

            List<File> jniVariants = new ArrayList<>();
            for (File f : allSoFiles) {
                String name = f.getName().toLowerCase();
                if (name.equals("libgdalalljni.so")) {
                    jniVariants.add(0, f);
                } else if (name.startsWith("libgdalalljni") && !jniVariants.contains(f)) {
                    jniVariants.add(f);
                }
            }

            if (!jniVariants.isEmpty()) {
                for (File so : allSoFiles) {
                    if (jniVariants.contains(so)) continue;
                    try { System.load(so.getAbsolutePath()); } catch (Throwable ignored) {}
                }

                for (File jni : jniVariants) {
                    try {
                        System.load(jni.getAbsolutePath());
                        System.out.println("[WMAPGEN-LIB] Linux GDAL library loaded from local native directory: " + jni.getAbsolutePath());
                        return;
                    } catch (UnsatisfiedLinkError e) {
                        System.out.println("[WMAPGEN-LIB] Skipping JNI variant '" + jni.getName() + "' (does not match installed system GDAL): " + e.getMessage());
                    }
                }
                System.err.println("[WMAPGEN-LIB] None of the " + jniVariants.size()
                        + " JNI variants in " + linuxDir.getAbsolutePath() + " matches the installed system GDAL!");
            }
        }

        try (java.io.InputStream in = TifWorldGenLib.class.getClassLoader().getResourceAsStream("native/Linux/libgdalalljni.so")) {
            if (in != null) {
                linuxDir.mkdirs();
                File bundled = new File(linuxDir, "libgdalalljni-bundled.so");
                java.nio.file.Files.copy(in, bundled.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    System.load(bundled.getAbsolutePath());
                    System.out.println("[WMAPGEN-LIB] Bundled Linux GDAL JNI extracted and loaded: " + bundled.getAbsolutePath());
                    return;
                } catch (UnsatisfiedLinkError e) {
                    System.out.println("[WMAPGEN-LIB] Bundled JNI does not match installed system GDAL, trying system bindings: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("[WMAPGEN-LIB] Could not extract bundled Linux JNI: " + e.getMessage());
        }

        try {
            System.loadLibrary("gdalalljni");
            System.out.println("[WMAPGEN-LIB] Linux system GDAL JNI library loaded successfully.");
            return;
        } catch (UnsatisfiedLinkError ignored) {}

        String[] commonSystemPaths = {
                "/usr/lib/libgdalalljni.so",
                "/usr/lib64/libgdalalljni.so",
                "/usr/lib/jni/libgdalalljni.so",
                "/usr/lib/x86_64-linux-gnu/jni/libgdalalljni.so",
                "/usr/lib/java/gdal/libgdalalljni.so",
                "/usr/lib/java/libgdalalljni.so",
                "/usr/share/java/gdal/libgdalalljni.so",
                "/usr/local/lib/libgdalalljni.so"
        };

        for (String path : commonSystemPaths) {
            File f = new File(path);
            if (f.exists()) {
                System.load(f.getAbsolutePath());
                System.out.println("[WMAPGEN-LIB] Linux system GDAL JNI loaded from explicit path: " + path);
                return;
            }
        }

        throw new RuntimeException(
                "Could not load 'libgdalalljni.so' (GDAL Java bindings).\n" +
                "Install GDAL with Java bindings for your distribution:\n" +
                "  Debian/Ubuntu: sudo apt install libgdal-java\n" +
                "  Fedora:        sudo dnf install gdal-java\n" +
                "  Arch:          sudo pacman -S gdal + put a matching libgdalalljni.so into .minecraft/tifworldgen/native/Linux/\n" +
                "The JNI bridge version MUST match your installed system GDAL version.");
    }

    private static void collectFiles(File dir, String extension, List<File> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectFiles(file, extension, result);
            } else if (file.getName().toLowerCase().endsWith(extension)) {
                result.add(file);
            }
        }
    }

    private static File findFile(File dir, String fileName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFile(file, fileName);
                if (found != null) return found;
            } else if (file.getName().equalsIgnoreCase(fileName)) {
                return file;
            }
        }
        return null;
    }
}