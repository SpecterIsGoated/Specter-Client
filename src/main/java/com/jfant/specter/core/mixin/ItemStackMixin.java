package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.FinishUsingItemEvent;
import com.jfant.specter.core.events.entity.player.StoppedUsingItemEvent;
import com.jfant.specter.core.events.game.ItemStackTooltipEvent;
import com.jfant.specter.core.utils.Utils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemStack.class})
public abstract class ItemStackMixin {
   @ModifyReturnValue(
      method = {"getTooltipLines"},
      at = {@At("RETURN")}
   )
   private List<Component> onGetTooltip(List<Component> original) {
      if (Utils.canUpdate()) {
         ItemStackTooltipEvent event = (ItemStackTooltipEvent)SpecterCore.EVENT_BUS.post(new ItemStackTooltipEvent((ItemStack)this, original));
         return event.list();
      } else {
         return original;
      }
   }

   @Inject(
      method = {"finishUsingItem"},
      at = {@At("HEAD")}
   )
   private void onFinishUsing(Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> info) {
      if (user == SpecterCore.mc.player) {
         SpecterCore.EVENT_BUS.post(FinishUsingItemEvent.get((ItemStack)this));
      }
   }

   @Inject(
      method = {"releaseUsing"},
      at = {@At("HEAD")}
   )
   private void onStoppedUsing(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo info) {
      if (user == SpecterCore.mc.player) {
         SpecterCore.EVENT_BUS.post(StoppedUsingItemEvent.get((ItemStack)this));
      }
   }
}
