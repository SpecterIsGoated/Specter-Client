package com.jfant.specter.mixin;

import com.jfant.specter.modules.Reach;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Player.class})
public abstract class ReachMixin {
   @ModifyReturnValue(
      method = {"entityInteractionRange"},
      at = {@At("RETURN")}
   )
   private double specter$entityRange(double range) {
      return (Object)this == Minecraft.getInstance().player ? Reach.entityRange(range) : range;
   }

   @ModifyReturnValue(
      method = {"blockInteractionRange"},
      at = {@At("RETURN")}
   )
   private double specter$blockRange(double range) {
      return (Object)this == Minecraft.getInstance().player ? Reach.blockRange(range) : range;
   }
}
