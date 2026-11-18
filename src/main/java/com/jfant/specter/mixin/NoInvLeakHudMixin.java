package com.jfant.specter.mixin;

import com.jfant.specter.modules.NoInvLeak;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class NoInvLeakHudMixin {
   @Unique
   private static final Identifier SPECTER$SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");

   @ModifyVariable(
      method = {"extractSlot"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private ItemStack specter$standInHotbar(ItemStack stack, @Local(argsOnly = true,ordinal = 2) int seed) {
      return NoInvLeak.hotbar(stack, seed);
   }

   @Inject(
      method = {"extractSelectedItemName"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$noItemName(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (NoInvLeak.hidesItemName()) {
         ci.cancel();
      }
   }

   @WrapWithCondition(
      method = {"extractItemHotbar"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
      )}
   )
   private boolean specter$noSelection(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
      return !NoInvLeak.hidesSelectedSlot() || !SPECTER$SELECTION.equals(sprite);
   }
}
