package com.example.tifworldgen.commands;

import com.example.tifworldgen.TifChunkGenerator;
import com.example.tifworldgen.config.ConfigManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(modid = "tifworldgen")
public class TifAdminCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tifworldgen")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reload").executes(TifAdminCommands::runReload))
                        .then(Commands.literal("maps").executes(TifAdminCommands::runMaps))
                        .then(Commands.literal("info").executes(TifAdminCommands::runInfo))
                        .then(Commands.literal("config").executes(TifAdminCommands::runConfigShow))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(TifAdminCommands::runSet))))
                        .then(Commands.literal("map")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                                        .executes(TifAdminCommands::runMapAdd))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(TifAdminCommands::runMapRemove))))
        );
    }

    private static int runReload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.load();
        int reloaded = applyToGenerators();
        if (reloaded == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] No TifWorldGen world found in this server."), false);
            return 0;
        }
        final int count = reloaded;
        ctx.getSource().sendSuccess(() -> Component.literal("§a[tifworldgen] Config reloaded and map readers refreshed (" + count + " level(s))."), false);
        return 1;
    }

    private static int applyToGenerators() {
        int reloaded = 0;
        var source = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (source == null) return 0;
        for (ServerLevel level : source.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof TifChunkGenerator generator) {
                generator.updateReadersIfNeeded();
                reloaded++;
            }
        }
        return reloaded;
    }

    private static int runSet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value").trim();
        if (ConfigManager.configData == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] Config not loaded."), false);
            return 0;
        }
        try {
            Field field = resolveField(ConfigManager.configData, key);
            if (field == null) {
                ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] Unknown key: " + key), false);
                return 0;
            }
            Object parsed = parseValue(field.getType(), value);
            if (parsed == null) {
                ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] Cannot put '" + value + "' into " + key + " (" + field.getType().getSimpleName() + ")"), false);
                return 0;
            }
            Object parent = resolveParent(ConfigManager.configData, key);
            Object old = field.get(parent);
            field.set(parent, parsed);
            ConfigManager.save();
            applyToGenerators();
            final String k = key;
            final String o = String.valueOf(old);
            final String n = String.valueOf(parsed);
            ctx.getSource().sendSuccess(() -> Component.literal("§a[tifworldgen] " + k + ": §c" + o + " §7-> §a" + n + " §7(saved + applied)"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] set failed: " + e.getMessage()), false);
            return 0;
        }
    }

    private static Object resolveParent(Object root, String path) throws Exception {
        String[] parts = path.split("\\.");
        Object node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            node = node.getClass().getField(parts[i]).get(node);
        }
        return node;
    }

    private static Field resolveField(Object root, String path) {
        try {
            String[] parts = path.split("\\.");
            Object node = root;
            for (int i = 0; i < parts.length - 1; i++) {
                node = node.getClass().getField(parts[i]).get(node);
            }
            return node.getClass().getField(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object parseValue(Class<?> type, String value) {
        try {
            if (type == int.class || type == Integer.class) return Integer.parseInt(value);
            if (type == long.class || type == Long.class) return Long.parseLong(value);
            if (type == double.class || type == Double.class) return Double.parseDouble(value);
            if (type == float.class || type == Float.class) return Float.parseFloat(value);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
            if (type == String.class) return value;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static int runMapAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String path = StringArgumentType.getString(ctx, "path").trim();
        if (ConfigManager.configData == null) return 0;
        boolean exists = new File(path).exists();
        List<ConfigManager.CustomMap> maps = ConfigManager.configData.maps;
        maps.removeIf(m -> m.name.equals(name));
        maps.add(new ConfigManager.CustomMap(name, path));
        ConfigManager.save();
        applyToGenerators();
        final String n = name;
        final String p = path;
        final boolean ok = exists;
        ctx.getSource().sendSuccess(() -> Component.literal((ok ? "§a[OK] " : "§e[FILE MISSING] ") + "§7map '" + n + "' -> " + p
                + " §7(use: /tifworldgen set waterBuilder.buildFromMap \"" + n + "\" or terrainBuilder/biomeBuilder)"), false);
        return 1;
    }

    private static int runMapRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (ConfigManager.configData == null) return 0;
        boolean removed = ConfigManager.configData.maps.removeIf(m -> m.name.equals(name));
        ConfigManager.save();
        applyToGenerators();
        final String n = name;
        final boolean r = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(r
                ? "§a[tifworldgen] map '" + n + "' removed."
                : "§c[tifworldgen] map '" + n + "' not found."), false);
        return r ? 1 : 0;
    }

    private static int runConfigShow(CommandContext<CommandSourceStack> ctx) {
        if (ConfigManager.configData == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] Config not loaded."), false);
            return 0;
        }
        var c = ConfigManager.configData;
        var t = c.terrainBuilder;
        var w = c.waterBuilder;
        var b = c.biomeBuilder;
        var u = c.undergroundBuilder;
        var s = c.seasonBuilder;
        ctx.getSource().sendSuccess(() -> Component.literal("§b[tifworldgen] Effective config:"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7terrain: §f" + t.buildFromMap + " §8| minY=" + t.minY + " maxY=" + t.maxY
                + " mapMin=" + t.mapMinHeight + " mapMax=" + t.mapMaxHeight), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7        slope: threshold=" + t.baseSlopeThreshold + " yFactor=" + t.slopeThresholdYFactor
                + " block=" + t.slopeBlock), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7water:   §f" + w.buildFromMap + " §8| seaLevel=" + w.seaLevel), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7biome:   §f" + b.buildFromMap + " §8| rules=" + (b.rules == null ? 0 : b.rules.size())), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7caves:   §f" + (u != null && u.generateCaves ? "ON" : "OFF")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7seasons: freezeY summer=" + s.summerFreezeY + " winter=" + s.winterFreezeY + " spring/autumn=" + s.springAutumnFreezeY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§8tip: /tifworldgen set <key> <value>, e.g. set terrainBuilder.minY 200"), false);
        return 1;
    }

    private static int runMaps(CommandContext<CommandSourceStack> ctx) {
        if (ConfigManager.configData == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[tifworldgen] Config not loaded."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§b[tifworldgen] Maps:"), false);
        if (ConfigManager.configData.maps != null && !ConfigManager.configData.maps.isEmpty()) {
            for (ConfigManager.CustomMap cm : ConfigManager.configData.maps) {
                String usage = describeUsage(cm.name);
                reportMap(ctx, usage, cm.name, cm.path);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(" §7(no maps configured - use /tifworldgen map add)"), false);
        }
        return 1;
    }

    private static String describeUsage(String mapName) {
        StringBuilder sb = new StringBuilder();
        if (mapName.equals(ConfigManager.configData.terrainBuilder.buildFromMap)) sb.append("terrain ");
        if (mapName.equals(ConfigManager.configData.waterBuilder.buildFromMap)) sb.append("water ");
        if (mapName.equals(ConfigManager.configData.biomeBuilder.buildFromMap)) sb.append("biome ");
        if (ConfigManager.configData.structureBuilder != null && mapName.equals(ConfigManager.configData.structureBuilder.buildFromMap)) sb.append("structure ");
        return sb.length() == 0 ? "unused" : sb.toString().trim();
    }

    private static void reportMap(CommandContext<CommandSourceStack> ctx, String label, String name, String path) {
        String pathToShow = path == null || path.isEmpty() ? "(not set)" : path;
        boolean exists = path != null && !path.isEmpty() && new File(path).exists();
        String state = exists ? "§a[OK]" : "§c[MISSING]";
        ctx.getSource().sendSuccess(() -> Component.literal(" " + state + " §7[" + label + "] §f" + name + " §8-> " + pathToShow), false);
    }

    private static int runInfo(CommandContext<CommandSourceStack> ctx) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        boolean found = false;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof TifChunkGenerator generator) {
                found = true;
                var reader = generator.getHeightMapReader();
                int w = reader.getTiffWidth();
                int h = reader.getTiffHeight();
                if (w > 0 && h > 0) {
                    final int fw = w, fh = h;
                    final String p = reader.getPath();
                    final String dim = level.dimension().location().toString();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b[tifworldgen] " + dim + ": heightmap " + fw + "x" + fh + " §8-> " + p), false);
                } else {
                    final String dim = level.dimension().location().toString();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§e[tifworldgen] " + dim + ": no heightmap loaded (check /tifworldgen maps)"), false);
                }
            }
        }
        return found ? 1 : 0;
    }
}
