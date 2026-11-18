package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Hud.class})
public abstract class NameProtectHudMixin {
   @ModifyVariable(
      method = {"setTitle"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Component specter$protectTitle(Component title) {
      return NameProtectPlus.protect(title);
   }

   @ModifyVariable(
      method = {"setSubtitle"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Component specter$protectSubtitle(Component subtitle) {
      return NameProtectPlus.protect(subtitle);
   }

   @ModifyVariable(
      method = {"setOverlayMessage"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Component specter$protectActionBar(Component message) {
      return NameProtectPlus.protect(message);
   }

   @ModifyExpressionValue(
      method = {"displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectSidebarTitle(Component original) {
      return NameProtectPlus.protect(original);
   }

   @ModifyExpressionValue(
      method = {"displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         ordinal = 0,
         target = "Lnet/minecraft/client/gui/Hud$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectSidebarLineWidth(Component original) {
      return NameProtectPlus.protect(original);
   }

   @ModifyExpressionValue(
      method = {"displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         ordinal = 1,
         target = "Lnet/minecraft/client/gui/Hud$1DisplayEntry;name:Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectSidebarLineDraw(Component original) {
      return NameProtectPlus.protect(original);
   }
}
