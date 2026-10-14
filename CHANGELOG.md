# Changelog

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
