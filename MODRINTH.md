# TifWorldGen – Modrinth page (final draft)

> ## AKO POUŽIŤ TENTO SÚBOR
> 1. Sekcia **PROJECT INFO** = hodnoty pre formuláre (nelepiť nikam ako text)
> 2. Sekcia **DESCRIPTION** = skopíruj DOCELA (od `# TifWorldGen` po koniec) do Description poľa cez `</>` source mód
> 3. **Obrázky**: najprv uploadni do Galerie projektu (Edit → Gallery), potom na každý obrázok klikni pravým → *Copy image address* → nahrad ním `SEM_URL_OBRAZKA_X` v popise
> 4. **Donate odkazy**: nahrad `SEM_KOFI_URL` / `SEM_PATREON_URL` svojimi stránkami (ak ešte nemáš, badge riadok s donate časťou vymaž)
> 5. Banner 1600×400 px odporúčam spraviť (napr. z rozrezaného screenshotu + logo)

---

## PROJECT INFO (pre formuláre)

- **Name:** TifWorldGen
- **Slug:** `tifworldgen` · **Type:** Mod · **Loaders:** Forge · **Game versions:** 1.20.1
- **License:** Arr (All Rights Reserved)
- **Categories:** Worldgen (+ prípadne Adventure/Tech)
- **Environment:** Client & Server
- **Summary:**
  `Build real-world terrain in Minecraft 1.20.1 from your own GIS GeoTIFF maps – heightmaps, rivers, lakes, forests – with seasonal snow lines and Distant Horizons LOD pregeneration.`

### Dependencies (pri vytváraní verzie)

| Mod | Requirement |
|---|---|
| Serene Seasons (`serene-seasons`) | Required |
| GlitchCore (`glitchcore`) | Required |
| Distant Horizons (`distanthorizons`) | Optional |
| Snow! Real Magic (`snow-real-magic`) | Optional |

### Galéria – navrhované obrázky

| Obrázok | Súbor (kandidát) | Popis pri uploade |
|---|---|---|
| Banner/hero | (vyrobiť 1600×400) | TifWorldGen – real-world terrain |
| Terén + rieky | `2026-09-01_17.35.56.png` | Real Slovakia terrain with carved rivers |
| Hory/lesy | `2026-09-01_17.17.56.png` | Altitude-banded biomes |
| Zima | (doskákať – snežná línia) | Seasonal snow line (Serene Seasons sync) |
| Jaskyne | (doskákať) | Vanilla-style caves following real terrain |
| GUI | (F1 + O) | In-game map import & biome rules (key O) |

---

## DESCRIPTION (skopíruj odtiaľto nadol ↓↓↓)

<div align="center">

# 🌍 TifWorldGen

**Build the real world in Minecraft – from your own GIS data.**

![Banner](SEM_URL_BANNERU)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/tifworldgen?label=Downloads&logo=modrinth)](https://modrinth.com/mod/tifworldgen)
[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/tifworldgen?logo=modrinth)](https://modrinth.com/mod/tifworldgen)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-informational)](https://modrinth.com/mod/tifworldgen)
[![Ko-fi](https://img.shields.io/badge/☕-Buy%20me%20a%20coffee-F16061?logo=ko-fi)](SEM_KOFI_URL)
[![Patreon](https://img.shields.io/badge/-Become%20a%20patron-FF424D?logo=patreon)](SEM_PATREON_URL)

</div>

---

TifWorldGen replaces vanilla world generation with **your own GIS data**. Feed it a heightmap of your country, region or city and it builds a 1:1 Minecraft world – hills, valleys, rivers, lakes and real forest coverage – powered by the industry-standard **GDAL** geospatial library.

Built and tested on a full real-world dataset: the entire **Slovakia DMR** terrain model (73,000 × 48,000 blocks, 14 GB GeoTIFF).

![Real terrain with rivers](SEM_URL_OBRAZKA_1)

## ✨ Features

- 🏔️ **Real terrain from heightmaps** – Float32 GeoTIFF, automatic min/max detection or manual height remapping (e.g. 1 m = 1 block)
- 🌊 **Rivers & lakes from water masks** – adaptive riverbed carving (shallow banks, deeper mid-river), organic riverbed blocks
- 🌲 **Real forests & biomes** – map forest classes to any biome per altitude band (broadleaf → forest, coniferous → old growth taiga, peaks → snowy slopes)
- 🏰 **Structure whitelist** – allow villages & structures only where your map says
- 🕳️ **Vanilla-style caves** – cheese/spaghetti/noodle caves that follow your terrain, aquifers, lava lakes, Deep Dark / Lush / Dripstone underground biomes
- ❄️ **Seasonal snow line** – Serene Seasons integration: rain below, snow above a moving altitude freeze line
- 🔭 **Distant Horizons LOD pregeneration** – `/tifworldgen dhpregen` fills DH with terrain, rivers and trees without generating real chunks
- ⚙️ **In-game config GUI** (press **O**) – map import with native file browser, biome rule editor with presets, slope & water tuning

![Biomes](SEM_URL_OBRAZKA_2)

## 📦 Installation

**1.** Install **Forge 47.x** for Minecraft **1.20.1** and drop both jars into `mods/`:
- `tifworldgen-<version>.jar` + `tifworldgen-lib-<version>.jar`

**2.** Install dependencies:

| Mod | Required | Why |
|---|---|---|
| [Serene Seasons](https://modrinth.com/mod/serene-seasons) (+ GlitchCore) | ✅ Yes | seasonal snow line integration |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 🔵 Optional | LOD pregeneration |
| [Snow! Real Magic](https://modrinth.com/mod/snow-real-magic) | 🔵 Optional | nicer snow stacking |

**3. GDAL native libraries** (the geospatial engine – pick your platform):

### 🪟 Windows
1. Install the [Microsoft Visual C++ Redistributable 2015–2022 (x64)](https://aka.ms/vs/17/release/vc_redist.x64.exe)
2. Download a GDAL x64 build from [GIS Internals](https://gisinternals.com/release.php) (e.g. `release-1929-x64-gdal-3-12-1-mapserver-8-6-0`)
3. Extract the whole release into `.minecraft/tifworldgen/native/Win/` (must contain `gdal.dll` + `gdalalljni.dll` + ~40 DLLs; subfolders are scanned; a README with instructions is generated there on first launch)

### 🐧 Linux
1. Install your distribution's GDAL package:
   - Arch: `sudo pacman -S gdal` *(then download `libgdalalljni.so` from the [GitHub repository root](https://github.com/MortainColors/TifWorldGen) into `.minecraft/tifworldgen/native/Linux/`)*
   - Debian/Ubuntu: `sudo apt install gdal-bin` *(+ `libgdal-java` if the version differs)*
   - Fedora: `sudo dnf install gdal` *(+ `gdal-java` if the version differs)*
2. A README is generated in `.minecraft/tifworldgen/native/Linux/` explaining all options

**4.** Create a new world → world type **“Tif World Map”** → press **O** to import your maps

## 🗺️ Map formats

| Map | Format | Notes |
|---|---|---|
| Heightmap | Float32 GeoTIFF | 1 px = 1 block ideal; min/max auto-detected |
| Water mask | Byte GeoTIFF | white (≥128) = water |
| Forest/biome map | classes 0/1/2 or RGB | auto-scaled to heightmap resolution; Bit or HEX rules |

## ⌨️ Commands

| Command | Description |
|---|---|
| `/tifworldgen dhpregen radius <1–4000>` | set LOD pregeneration radius |
| `/tifworldgen dhpregen genaboveylevel <Y>` | pregenerate only above a height |
| `/tifworldgen dhpregen prepare` / `start` / `stop` | run LOD pregeneration |
| `/tifworldgen sereneseason status` | current season & freeze line |

## ❤️ Credits & Support

Built on [GDAL](https://gdal.org) · [Distant Horizons](https://modrinth.com/mod/distanthorizons) LOD integration · [Serene Seasons](https://modrinth.com/mod/serene-seasons) compatibility · [Snow! Real Magic](https://modrinth.com/mod/snow-real-magic) · terrain test data: Slovakia DMR.

If you enjoy the mod, consider supporting development:

[![Ko-fi](https://img.shields.io/badge/☕-Ko--fi-F16061?logo=ko-fi)](SEM_KOFI_URL) [![Patreon](https://img.shields.io/badge/-Patreon-FF424D?logo=patreon)](SEM_PATREON_URL)

## 📄 License

All Rights Reserved.
