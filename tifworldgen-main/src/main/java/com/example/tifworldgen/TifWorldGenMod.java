package com.example.tifworldgen;

import com.example.tifworldgen.config.ConfigManager;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(TifWorldGenMod.MODID)
public class TifWorldGenMod {
    public static final String MODID = "tifworldgen";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    public static final RegistryObject<Codec<TifChunkGenerator>> TIFF_GENERATOR =
            CHUNK_GENERATORS.register("tiff_generator", () -> TifChunkGenerator.CODEC);

    public TifWorldGenMod() {
        ConfigManager.init();
        LOGGER.info("WMAPGEN: Hlavný mod sa inicializuje...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CHUNK_GENERATORS.register(modEventBus);

        if (ModList.get().isLoaded("distanthorizons")) {
            MinecraftForge.EVENT_BUS.register(BackgroundDHPreGenerator.class);
            LOGGER.info("WMAPGEN: Distant Horizons detected, LOD pregeneration enabled.");
        } else {
            LOGGER.info("WMAPGEN: Distant Horizons not detected, LOD pregeneration disabled.");
        }

        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof TifChunkGenerator generator) {
                try {
                    generator.close();
                } catch (Exception e) {
                    LOGGER.warn("WMAPGEN: Failed to close GDAL datasets for a level", e);
                }
            }
        }
    }
}