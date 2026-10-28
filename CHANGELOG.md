# Changelog

## 0.1.0-beta2 — stability & worldgen fixes

### 🐛 Fixed

- **Worlds now save correctly on disconnect** – map readers were closed before the
  world finished saving, which could abort the save entirely, or silently complete
  in-flight chunks as empty "ghost chunks" when leaving during terrain generation.
  Readers are now released only after the world is fully saved (`ServerStoppedEvent`).
- **No more ghost chunks** – chunk generation refuses to run without a working
  heightmap reader: the chunk is left ungenerated and retried later, instead of being
  saved as empty terrain.
- **Generation errors can no longer destroy chunks** – every generation step (terrain,
  biomes, surface, caves, structures, decorations, mob spawning) is guarded: on an
  unexpected error the chunk keeps safe fallback terrain and the error is logged with
  the chunk position and a full stack trace (log messages in English).
- **Color Mapping screen at high GUI scale** – the tab no longer overflows the screen
  at GUI scale Auto / 4 (small windows). Widgets stay vanilla-sized, long buttons adapt
  their width, overlong map names are shortened with "…" and show the full name in a
  tooltip, and the whole tab scrolls when the window is short.

### ✨ Added

- **River and ocean biomes from the water mask** – narrow water areas get a river
  biome, large lakes get an ocean biome (kelp-friendly). River depth adapts to the
  width of the water area.
- **Unified map list (`maps[]`)** – every map is an equal entry in one list; no more
  "custom vs fixed" split. Old configurations migrate automatically on first start.

### 🔧 Changed

- Map layer generators always read through the current map reader instance (no stale
  references after a config reload).
- Slovak in-game log messages with a unified `[TIFWORLDGEN]` prefix.
- Documentation and in-jar descriptions updated.

## 0.1.0-beta1 — first public beta

### ✨ Highlights

- **Built-in pure-Java GeoTIFF engine** – the mod no longer depends on GDAL or any native
  library. Reads classic TIFF and BigTIFF (tested with a 14 GB heightmap, designed for
  100 GB+ files), stripped and tiled layouts, LZW and Deflate compression. Verified
  pixel-perfect against GDAL before the switch (336,959 sampled pixels, 0 differences).
- **Zero-install experience** – no DLLs, no system packages, no `.so` files. Works on
  Windows, Linux **and macOS** out of the box: just drop the jar into `mods/`.
- **Dedicated server support** – create worlds with `level-type=tifworldgen:void_preset`
  and manage everything in-game (verified on a production Forge server):
  - `/tifworldgen info` – loaded heightmap size and path
  - `/tifworldgen maps` – configured maps overview with missing-file check
  - `/tifworldgen config` – effective configuration overview
  - `/tifworldgen reload` – reload config and map readers without restart
  - `/tifworldgen set <key> <value>` – change any setting in-game (saved + applied instantly)
  - `/tifworldgen map add/remove` – register map files without editing JSON
  - works over RCON for headless servers
- **Portable configuration** – map paths now support relative paths (resolved against the
  game directory on both client and server). Configure everything comfortably in
  singleplayer, then copy `config/tifworldgen.json` and the `tifworldgen/maps/` folder
  to the server – no edits needed.
- **Single-file distribution** – the all-in-one jar bundles the library inside (Forge
  JarJar), so players download exactly one file.

### 🔧 Changed

- Rewritten map layer readers (`HeightMapReader`, `WaterLayer`, `ForestLayer`,
  `StructureLayer`) on top of the new engine – also removes the thread-local dataset
  handling and its file-handle leaks.
- GDAL completely removed from the repository, build and in-jar descriptions.

### 🔒 Privacy

- Fully offline: no telemetry, no analytics, no network communication of any kind.
  The mod only reads the map files the player/admin selects.

### 📦 Upgrading from alpha builds

- Delete the old `tifworldgen/native/` folder from your game directory – it is no
  longer used.
- If you configured maps by absolute paths, they keep working; relative paths
  (relative to the game directory) are now recommended.
