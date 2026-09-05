
package com.example.tifworldgen.seasons;

import com.example.tifworldgen.config.ConfigManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

import sereneseasons.api.season.SeasonHelper;
import sereneseasons.api.season.ISeasonState;

@Mod.EventBusSubscriber(modid = "tifworldgen")
public class TifSeasonManager {

    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    public static volatile int CURRENT_FREEZE_LINE = 3000;

    public static volatile boolean isPaused = false;
    public static volatile Season forcedSeason = null;
    private static volatile Season lastKnownSeason = null;
    private static volatile String lastSeasonName = "";

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide()) {
            updateFreezeLine(event.level);
        }
    }

    @Mod.EventBusSubscriber(modid = "tifworldgen", value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    updateFreezeLine(level);
                }
            }
        }
    }

    public static void updateFreezeLine(Level level) {
        try {
            Season season;

            if (forcedSeason != null) {
                season = forcedSeason;
            } else if (isPaused) {
                season = (lastKnownSeason != null) ? lastKnownSeason : getCalculatedSeason(level);
            } else {
                season = getCalculatedSeason(level);
                lastKnownSeason = season;
            }

            String seasonName = season.name();
            int freezeLine = 200;

            if (ConfigManager.configData != null && ConfigManager.configData.seasonBuilder != null) {
                freezeLine = switch (season) {
                    case SUMMER -> ConfigManager.configData.seasonBuilder.summerFreezeY;
                    case WINTER -> ConfigManager.configData.seasonBuilder.winterFreezeY;
                    default -> ConfigManager.configData.seasonBuilder.springAutumnFreezeY;
                };
            } else {
                freezeLine = switch (season) {
                    case SUMMER -> 3000;
                    case WINTER -> 200;
                    default -> 1300;
                };
            }

            CURRENT_FREEZE_LINE = freezeLine;

            if (!seasonName.equals(lastSeasonName)) {
                System.out.println("[TIF-SEASONS] 🌍 Synchronizácia so Serene Seasons úspešná. Nastavené na: " + seasonName + " (Y = " + freezeLine + ")");
                lastSeasonName = seasonName;
            }
        } catch (Throwable t) {
            int summerY = 3000, winterY = 200, springAutumnY = 1300;
            try {
                if (ConfigManager.configData != null && ConfigManager.configData.seasonBuilder != null) {
                    summerY = ConfigManager.configData.seasonBuilder.summerFreezeY;
                    winterY = ConfigManager.configData.seasonBuilder.winterFreezeY;
                    springAutumnY = ConfigManager.configData.seasonBuilder.springAutumnFreezeY;
                }
            } catch (Throwable ignored) {
            }
            if (forcedSeason == Season.SUMMER) {
                CURRENT_FREEZE_LINE = summerY;
            } else if (forcedSeason == Season.WINTER) {
                CURRENT_FREEZE_LINE = winterY;
            } else {
                CURRENT_FREEZE_LINE = springAutumnY;
            }
        }
    }

    private static Season getCalculatedSeason(Level level) {
        try {
            ISeasonState ssState = SeasonHelper.getSeasonState(level);
            return switch (ssState.getSeason()) {
                case SPRING -> Season.SPRING;
                case SUMMER -> Season.SUMMER;
                case AUTUMN -> Season.AUTUMN;
                case WINTER -> Season.WINTER;
            };
        } catch (Throwable t) {
            return getFallbackSeason(level);
        }
    }

    private static Season getFallbackSeason(Level level) {
        long totalTicks = level.getGameTime();
        long currentDay = totalTicks / 24000;
        long dayInYear = (currentDay + 90) % 240;

        if (dayInYear < 60) return Season.SPRING;
        if (dayInYear < 120) return Season.SUMMER;
        if (dayInYear < 180) return Season.AUTUMN;
        return Season.WINTER;
    }
}