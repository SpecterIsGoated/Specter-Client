package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({TridentItem.class})
public abstract class TridentItemMixin {
   @Inject(
      method = {"releaseUsing"},
      at = {@At("HEAD")}
   )
   private void onStoppedUsingHead(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> info) {
      if (user == SpecterCore.mc.player) {
         Utils.isReleasingTrident = true;
      }
   }

   @Inject(
      method = {"releaseUsing"},
      at = {@At("TAIL")}
   )
   private void onStoppedUsingTail(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> info) {
      if (user == SpecterCore.mc.player) {
         Utils.isReleasingTrident = false;
      }
   }
}
