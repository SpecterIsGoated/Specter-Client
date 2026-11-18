package com.jfant.specter.mixin;

import com.jfant.specter.modules.NoInvLeak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractContainerScreen.class})
public abstract class NoInvLeakScreenMixin {
   @Shadow
   protected Slot hoveredSlot;

   @WrapOperation(
      method = {"extractSlot"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;",
         ordinal = 0
      )}
   )
   private ItemStack specter$standInSlot(Slot slot, Operation<ItemStack> original) {
      return NoInvLeak.slot((ItemStack)original.call(new Object[]{slot}), slot);
   }

   @ModifyVariable(
      method = {"extractFloatingItem"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private ItemStack specter$standInCursor(ItemStack stack) {
      return NoInvLeak.cursor(stack);
   }

   @Inject(
      method = {"extractTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$noTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
      if (NoInvLeak.hidesTooltips() && NoInvLeak.ownSlot(this.hoveredSlot)) {
         ci.cancel();
      }
   }
}
