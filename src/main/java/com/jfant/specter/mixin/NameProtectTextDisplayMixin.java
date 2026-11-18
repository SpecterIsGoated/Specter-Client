package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.Display.TextDisplay.CachedInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({TextDisplay.class})
public abstract class NameProtectTextDisplayMixin {
   @Unique
   private int specter$nameGeneration;

   @ModifyExpressionValue(
      method = {"cacheDisplay"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         ordinal = 0,
         target = "Lnet/minecraft/world/entity/Display$TextDisplay;clientDisplayCache:Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
      )}
   )
   private CachedInfo specter$refreshLines(CachedInfo kept) {
      int generation = NameProtectPlus.generation();
      if (generation == this.specter$nameGeneration) {
         return kept;
      } else {
         this.specter$nameGeneration = generation;
         return null;
      }
   }

   @ModifyExpressionValue(
      method = {"cacheDisplay"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;text()Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectDisplayText(Component text) {
      return NameProtectPlus.protect(text);
   }
}
