package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
   @ModifyExpressionValue(
      method = {"shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getTeam()Lnet/minecraft/world/scores/PlayerTeam;"
      )}
   )
   private PlayerTeam hasLabelClientPlayerEntityGetScoreboardTeamProxy(PlayerTeam team) {
      return SpecterCore.mc.player == null ? null : team;
   }
}
