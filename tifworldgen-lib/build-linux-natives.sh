#!/bin/bash
# Build Linux Java bindingov GDAL proti aktualnemu systémovému GDAL (Arch).
# Vysledok: gdal.jar + libgdalalljni.so, presne zodpovedajúce verzii v /usr/lib.
# Použitie po každom upgrade systémového gdal: ./build-linux-natives.sh
set -euo pipefail

GDAL_VER="$(gdal-config --version)"          # musi sediet s nainstalovaným balíkom
SRC="${SRC:-$HOME/gdal-$GDAL_VER}"           # rozbaleny zdrojovy tarbal gdal-$GDAL_VER.tar.gz z https://gdal.org/download.html
BUILD="$(dirname "$0")/build-linux-natives"
JH="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"

[ -d "$SRC/swig" ] || { echo "Chýba zdroják $SRC – stiahni a rozbaľ gdal-$GDAL_VER.tar.gz"; exit 1; }

rm -rf "$BUILD"; mkdir -p "$BUILD/java" "$BUILD/jni"
cd "$SRC/swig/java"

# 1. Vygenerovat vsetkych 5 modulov (gdalconst je C, ostatne C++)
for t in gdal ogr osr gnm; do
    mkdir -p "$BUILD/java/org/gdal/$t"
    swig -Wall -I"$SRC/swig/include" -I"$SRC/swig/include/java" -I"$SRC/gdal" -c++ -java \
        -o "$BUILD/jni/${t}_wrap.cpp" -outdir "$BUILD/java/org/gdal/$t/" -package "org.gdal.$t" \
        "$SRC/swig/include/$t.i"
done
mkdir -p "$BUILD/java/org/gdal/gdalconst"
swig -Wall -I"$SRC/swig/include" -I"$SRC/swig/include/java" -I"$SRC/gdal" -java \
    -o "$BUILD/jni/gdalconst_wrap.c" -outdir "$BUILD/java/org/gdal/gdalconst/" -package org.gdal.gdalconst \
    "$SRC/swig/include/gdalconst.i"

# 2. Skompilovat JNI kniznicu proti systémovému libgdal
cd "$BUILD/jni"
gcc -c -fPIC -O2 -fno-strict-aliasing -I/usr/include -I"$JH/include" -I"$JH/include/linux" gdalconst_wrap.c -o gdalconst_wrap.o
for f in gdal ogr osr gnm; do
    g++ -c -fPIC -O2 -fno-strict-aliasing -std=c++17 -I/usr/include -I"$JH/include" -I"$JH/include/linux" "${f}_wrap.cpp" -o "${f}_wrap.o"
done
g++ -shared ./*.o -o "$BUILD/libgdalalljni.so" -L/usr/lib -lgdal

# 3. Skompilovat Java triedy a zabaliť do jar
# Pozor: triedy MUSIA byt pre Javu 17 (class file v. 61), inak Gradle/Minecraft build zlyha
find "$BUILD/java" -name "*.java" > "$BUILD/sources.txt"
"$JH/bin/javac" -nowarn --release 17 -d "$BUILD/classes" @"$BUILD/sources.txt"
"$JH/bin/jar" cf "$BUILD/gdal.jar" -C "$BUILD/classes" org

# 4. Nainstalovat do projektu (viditelne v repo pre stiahnutie usermi) a do herneho priecinka
# Pozor: .so sa uz NEbali do jara (CurseForge zakaz) - useri si ho stahuju odtialto z GitHubu
# alebo pouzivaju systemove baliky (libgdal-java / gdal-java).
PROJ="$(dirname "$0")"
cp "$BUILD/gdal.jar"                      "$PROJ/libs/gdal.jar"
mkdir -p "$PROJ/native/Linux"
cp "$BUILD/libgdalalljni.so"              "$PROJ/native/Linux/libgdalalljni.so"

GAME_NATIVE="${GAME_NATIVE:-$HOME/.minecraft/tifworldgen/native/Linux}"
mkdir -p "$GAME_NATIVE"
cp "$BUILD/libgdalalljni.so" "$GAME_NATIVE/libgdalalljni.so"

echo "OK: GDAL $GDAL_VER -> libs/gdal.jar + native/Linux/libgdalalljni.so (repo) + $GAME_NATIVE"
echo "Nezabudni commitnut+pushnut zmeny (useri stahuju .so z GitHubu) a rebuildnut mod."
