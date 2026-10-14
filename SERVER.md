# TifWorldGen – Dedicated Server Setup

TifWorldGen works on dedicated servers. No GUI is available there, so maps are
configured in `config/tifworldgen.json` and managed with commands.

## Requirements

- Forge **47.x** for Minecraft **1.20.1** (dedicated server)
- `tifworldgen-<version>-all.jar` in `mods/` (contains both the mod and the library)
- **Serene Seasons** + **GlitchCore** in `mods/` (required dependencies)
- Distant Horizons is **not needed** on the server (client-side mod; the mod detects its absence automatically)
- No native libraries are needed anywhere – the mod reads GeoTIFF files in pure Java

## 1. Create the world with TifWorldGen generator

In `server.properties` set:

```properties
level-type=tifworldgen\:void_preset
```

(The `\:` escape is required in properties files.)

## Recommended workflow: configure in singleplayer, deploy to server

The config format is identical in singleplayer and on a dedicated server, so the
easiest setup path is:

1. **Configure in singleplayer** – install the mod in your client, create a
   Tif World Map world, press **O** and set everything up with the GUI
   (map import, terrain range, biome rules...). You see the result instantly.
2. **Copy `config/tifworldgen.json`** from your client's `.minecraft/config/`
   to the server's `config/` folder.
3. **Copy the map files** to the same relative location on the server.

Map paths support **relative paths** (resolved against the game directory on
both client and server). If you keep maps in `tifworldgen/maps/`, the config
transfers 1:1 with zero edits. The in-game file picker automatically stores
relative paths for files inside the game directory. Absolute paths keep working
as before.

After copying, verify on the server:

```
/tifworldgen maps     <- everything [OK]?
/tifworldgen info     <- heightmap loaded?
```

## Alternative: configure maps directly on the server

Edit `config/tifworldgen.json` (created on first start) and point it to the map
files **on the server machine** (absolute paths):

```json
{
  "heightmapName": "Height Map TIF",
  "heightmapPath": "/srv/maps/height.tif",
  "forestmapName": "Forest Layer TIF",
  "forestmapPath": "/srv/maps/forest.tif",
  "watermapName": "Water/River TIFF",
  "watermapPath": "",
  "customMaps": [
    { "name": "Water Map", "path": "/srv/maps/water.tif" }
  ],
  "terrainBuilder": { "buildFromMap": "Height Map TIF", "minY": 234, "maxY": 1652, "...": "..." },
  "waterBuilder":    { "buildFromMap": "Water Map", "seaLevel": 0 },
  "biomeBuilder":    { "buildFromMap": "Forest Layer TIF", "rules": [] }
}
```

Tips:
- `terrainBuilder.minY` / `maxY` = world height range the map is stretched into.
- Water goes into `customMaps` and is referenced by name in `waterBuilder.buildFromMap`.
- Heightmap: Float32 GeoTIFF (BigTIFF supported, tested with 14 GB files).
- Water mask: Byte GeoTIFF (white >= 128 = water). Forest map: classes 0/1/2 or RGB.

## 3. Admin commands (OP level 2+)

| Command | Description |
|---|---|
| `/tifworldgen info` | shows loaded heightmap size and path per level |
| `/tifworldgen maps` | lists all configured maps, marks missing files |
| `/tifworldgen config` | shows the effective configuration overview |
| `/tifworldgen reload` | reloads `config/tifworldgen.json` and re-opens map readers (no restart needed) |
| `/tifworldgen set <key> <value>` | sets any config value in-game, saves and applies instantly |
| `/tifworldgen map add <name> <path>` | registers a map file (e.g. a water mask) |
| `/tifworldgen map remove <name>` | removes a registered map |
| `/tifworldgen sereneseason status` | current Serene Seasons season + dynamic freeze line |

`set` works on any scalar value, e.g.:

```
/tifworldgen set terrainBuilder.minY 200
/tifworldgen set terrainBuilder.maxY 1755
/tifworldgen set waterBuilder.seaLevel 63
/tifworldgen set terrainBuilder.baseSlopeThreshold 4
/tifworldgen set waterBuilder.buildFromMap "Water Map"
/tifworldgen set undergroundBuilder.generateCaves true
```

Workflow A (in-game, recommended): `/tifworldgen map add ...` + `/tifworldgen set ...`
– changes are saved to disk and applied immediately (new chunks only).

Workflow B (file): edit `config/tifworldgen.json` on the server, then run
`/tifworldgen reload`. New chunks will use the updated maps immediately
(already-generated chunks are never changed by Minecraft design).

Biome rules (`biomeBuilder.rules`) are the only structure still edited in the
JSON file directly.

## 4. Client connection

Players connect with a normal Forge 1.20.1 client with the same mods
(`tifworldgen-all.jar`, Serene Seasons + GlitchCore). The in-game config GUI
(key **O**) on the client only affects local/singleplayer worlds – on a server,
configuration is done on the server side.

## Verified

Tested with a production Forge 47.4.10 dedicated server: world generated from a
14 GB BigTIFF heightmap (pure-Java reader), Serene Seasons integration active,
admin commands working, Distant Horizons gracefully disabled.
