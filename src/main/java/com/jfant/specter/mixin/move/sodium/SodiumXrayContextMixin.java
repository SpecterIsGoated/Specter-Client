package com.jfant.specter.mixin.move.sodium;

import com.jfant.specter.move.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {AbstractBlockRenderContext.class},
   remap = false
)
public abstract class SodiumXrayContextMixin {
   @Shadow
   protected BlockState state;
   @Shadow
   protected BlockPos pos;
   @Shadow
   protected LevelSlice slice;

   @Inject(
      method = {"shouldDrawSide"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$force(Direction facing, CallbackInfoReturnable<Boolean> cir) {
      if (Xray.active() && !Xray.isBlocked(this.state.getBlock(), this.slice, this.pos)) {
         cir.setReturnValue(true);
      }
   }

   @ModifyReturnValue(
      method = {"shouldDrawSide"},
      at = {@At("RETURN")}
   )
   private boolean specter$drawSide(boolean original, Direction facing) {
      return Xray.active() ? Xray.drawSide(this.state, this.slice, this.pos, facing, original) : original;
   }
}
