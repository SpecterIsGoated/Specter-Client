package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderer.class})
public abstract class NameProtectNameTagMixin {
   @ModifyExpressionValue(
      method = {"extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectNameTag(Component original) {
      return NameProtectPlus.protect(original);
   }
}
