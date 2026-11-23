package com.jfant.specter.mixin;

import com.jfant.specter.modules.JetMech;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jet Mech: decides per frame whether a player is drawn as a mech, and poses it. */
@Mixin(AvatarRenderer.class)
public class MechAvatarRendererMixin {
   @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
      at = @At("RETURN")
   )
   private void specter$mech(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
      JetMech.extract(entity, state, partialTicks);
   }
}
