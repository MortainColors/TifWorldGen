
package com.example.tifworldgen.mixin.compat;

import com.example.tifworldgen.seasons.TifSeasonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "sereneseasons.season.SeasonHooks")
public class SereneSeasonsInjectionMixin {

    @Inject(
            method = "getBiomeTemperature(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void tifworldgen$overrideGetBiomeTemperatureReader(LevelReader level, Holder<Biome> biome, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (pos.getY() < TifSeasonManager.CURRENT_FREEZE_LINE) {
            cir.setReturnValue(0.8F);
        } else {
            if (TifSeasonManager.CURRENT_FREEZE_LINE < 2500) {
                cir.setReturnValue(0.0F);
            }
        }
    }

    @Inject(
            method = "getBiomeTemperature(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void tifworldgen$overrideGetBiomeTemperatureLevel(Level level, Holder<Biome> biome, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (pos.getY() < TifSeasonManager.CURRENT_FREEZE_LINE) {
            cir.setReturnValue(0.8F);
        } else {
            if (TifSeasonManager.CURRENT_FREEZE_LINE < 2500) {
                cir.setReturnValue(0.0F);
            }
        }
    }
}

