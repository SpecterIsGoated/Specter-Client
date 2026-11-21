package com.jfant.specter.mixin;

import com.jfant.specter.modules.RedstoneOptimizer;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redstone Optimizer: hold back redstone-only chunk re-renders and far redstone particles. */
@Mixin(ClientLevel.class)
public abstract class RedstoneOptimizerLevelMixin {
   @Inject(method = "setBlocksDirty", at = @At("HEAD"), cancellable = true)
   private void specter$redstoneSetBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
      RedstoneOptimizer m = RedstoneOptimizer.on();
      if (m != null && m.skipSetBlocksDirty(pos, oldState, newState)) {
         ci.cancel();
      }
   }

   @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
   private void specter$redstoneSendBlockUpdated(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo ci) {
      RedstoneOptimizer m = RedstoneOptimizer.on();
      if (m != null && m.skipSendBlockUpdated(pos, old, current, updateFlags)) {
         ci.cancel();
      }
   }

   @WrapWithCondition(
      method = "doAnimateTick",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
      )
   )
   private boolean specter$redstoneParticles(Block block, BlockState state, Level level, BlockPos pos, RandomSource random) {
      RedstoneOptimizer m = RedstoneOptimizer.on();
      return m == null || m.allowAnimateTick(block, pos);
   }
}
