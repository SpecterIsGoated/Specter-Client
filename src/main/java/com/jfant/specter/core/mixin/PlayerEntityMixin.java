package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.DropItemsEvent;
import com.jfant.specter.core.events.entity.player.ClipAtLedgeEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Player.class})
public abstract class PlayerEntityMixin extends LivingEntity {
   @Shadow
   public abstract Abilities getAbilities();

   protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level world) {
      super(entityType, world);
   }

   @Inject(
      method = {"isStayingOnGroundSurface"},
      at = {@At("HEAD")},
      cancellable = true
   )
   protected void clipAtLedge(CallbackInfoReturnable<Boolean> info) {
      if (this.level().isClientSide()) {
         ClipAtLedgeEvent event = (ClipAtLedgeEvent)SpecterCore.EVENT_BUS.post(ClipAtLedgeEvent.get());
         if (event.isSet()) {
            info.setReturnValue(event.isClip());
         }
      }
   }

   @Inject(
      method = {"drop"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropItem(ItemStack stack, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
      if (this.level().isClientSide() && !stack.isEmpty() && ((DropItemsEvent)SpecterCore.EVENT_BUS.post(DropItemsEvent.get(stack))).isCancelled()) {
         cir.setReturnValue(null);
      }
   }

   @Inject(
      method = {"isSpectator"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsSpectator(CallbackInfoReturnable<Boolean> info) {
      if (SpecterCore.mc.getConnection() == null) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"isCreative"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCreative(CallbackInfoReturnable<Boolean> info) {
      if (SpecterCore.mc.getConnection() == null) {
         info.setReturnValue(false);
      }
   }
}
