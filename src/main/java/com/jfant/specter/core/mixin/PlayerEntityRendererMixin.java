package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.player.Rotations;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AvatarRenderer.class})
public abstract class PlayerEntityRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity>
   extends LivingEntityRenderer<AvatarlikeEntity, AvatarRenderState, PlayerModel> {
   public PlayerEntityRendererMixin(Context ctx, PlayerModel model, float shadowRadius) {
      super(ctx, model, shadowRadius);
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V"},
      at = {@At("RETURN")}
   )
   private void updateRenderState$rotations(AvatarlikeEntity player, AvatarRenderState state, float f, CallbackInfo info) {
      if (Rotations.rotating && player == SpecterCore.mc.player) {
         state.yRot = 0.0F;
         state.bodyRot = Rotations.serverYaw;
         state.xRot = Rotations.serverPitch;
      }
   }
}
