package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BossHealthOverlay.class})
public abstract class NameProtectBossBarMixin {
   @ModifyExpressionValue(
      method = {"extractRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectBossName(Component original) {
      return NameProtectPlus.protect(original);
   }
}
