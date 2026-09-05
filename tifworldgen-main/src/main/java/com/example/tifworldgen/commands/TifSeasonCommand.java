
package com.example.tifworldgen.commands;

import com.example.tifworldgen.seasons.TifSeasonManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import sereneseasons.api.season.SeasonHelper;
import sereneseasons.api.season.ISeasonState;

@Mod.EventBusSubscriber(modid = "tifworldgen")
public class TifSeasonCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tifworldgen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("sereneseason")
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource()))
                        )
                )
        );
    }

    private static int showStatus(CommandSourceStack source) {
        try {
            Level level = source.getLevel();
            ISeasonState ssState = SeasonHelper.getSeasonState(level);

            long totalTicks = level.getGameTime();
            long currentDay = totalTicks / 24000;

            source.sendSuccess(() -> Component.literal("§2=== TIF X SERENE SEASONS COMPATIBILITY ==="), false);
            source.sendSuccess(() -> Component.literal("§7Total World Day: §f" + currentDay), false);
            source.sendSuccess(() -> Component.literal("§7Serene Seasons State: §e" + ssState.getSeason().name() + " (§6" + ssState.getSubSeason().name() + "§e)"), false);
            source.sendSuccess(() -> Component.literal("§7TIF Dynamic Freeze Line: §bY = " + TifSeasonManager.CURRENT_FREEZE_LINE), false);
            source.sendSuccess(() -> Component.literal("§2=========================================="), false);

        } catch (Throwable t) {
            source.sendFailure(Component.literal("§c[TifWorldGen] Failed to read Serene Seasons bridge data."));
        }
        return 1;
    }
}
