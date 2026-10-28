package com.example.tifworldgen;

import com.example.tifworldgen.config.ConfigManager;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
        LOGGER.info("TIFWORLDGEN: Hlavný mod sa inicializuje...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CHUNK_GENERATORS.register(modEventBus);

        if (ModList.get().isLoaded("distanthorizons")) {
            MinecraftForge.EVENT_BUS.register(BackgroundDHPreGenerator.class);
            LOGGER.info("TIFWORLDGEN: Distant Horizons detekované – LOD pregenerácia povolená.");
        } else {
            LOGGER.info("TIFWORLDGEN: Distant Horizons nenájdené – LOD pregenerácia vypnutá.");
        }

        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))) return;
        if (!(level.getChunkSource().getGenerator() instanceof TifChunkGenerator generator)) return;

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("tifworld_center_spawn_done")) return;

        int surfaceY = generator.getHeightMapReader().getHeight(0, 0);
        if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) return;

        data.putBoolean("tifworld_center_spawn_done", true);
        player.teleportTo(level, 0.5D, surfaceY + 1.0D, 0.5D, 0.0F, 0.0F);
        LOGGER.info("TIFWORLDGEN: Hráč {} spawnutý v strede mapy (0, {}, 0).", player.getName().getString(), surfaceY + 1);
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        setMapCenterSpawn(event.getServer());
    }

    private void onServerStarted(ServerStartedEvent event) {
        setMapCenterSpawn(event.getServer());
    }

    private void setMapCenterSpawn(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        if (!(overworld.getChunkSource().getGenerator() instanceof TifChunkGenerator generator)) return;

        int surfaceY = generator.getHeightMapReader().getHeight(0, 0);
        if (surfaceY <= overworld.getMinBuildHeight() || surfaceY >= overworld.getMaxBuildHeight()) return;

        BlockPos spawn = new BlockPos(0, surfaceY + 1, 0);
        overworld.setDefaultSpawnPos(spawn, 0.0F);
        LOGGER.info("TIFWORLDGEN: World spawn nastavený do stredu mapy (0, {}, 0).", surfaceY + 1);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof TifChunkGenerator generator) {
                try {
                    generator.close();
                } catch (Exception e) {
                    LOGGER.warn("TIFWORLDGEN: Nepodarilo sa zatvoriť čítače máp pre level", e);
                }
            }
        }
    }
}