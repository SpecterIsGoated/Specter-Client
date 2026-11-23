package com.jfant.specter.mixin;

import com.jfant.specter.mech.MechAnim;
import com.jfant.specter.mech.MechRender;
import com.jfant.specter.modules.JetMech;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jet Mech: for a player drawn as a mech, submits the mech in place of the player model and skips the model and its
 * layers (armour, held items, cape, elytra...). Name tags, leashes, shadows and fire still come from vanilla.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MechLivingRendererMixin {
   private static final String SUBMIT =
      "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

   @Inject(method = SUBMIT, at = @At("HEAD"))
   private void specter$submitMech(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
      if (state instanceof AvatarRenderState avatar) {
         MechAnim anim = JetMech.mechOf(avatar);
         if (anim != null) MechRender.submit(avatar, anim, poseStack, collector, camera);
      }
   }

   @WrapWithCondition(
      method = SUBMIT,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
      )
   )
   private boolean specter$hideModel(
      SubmitNodeCollector collector, Model<?> model, Object modelState, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint,
      UvMapping uv, int outline, LivingEntityRenderState state
   ) {
      return !(state instanceof AvatarRenderState avatar) || JetMech.mechOf(avatar) == null;
   }

   @ModifyExpressionValue(
      method = SUBMIT,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"
      )
   )
   private boolean specter$hideLayers(boolean original, @Local(argsOnly = true) LivingEntityRenderState state) {
      return original && (!(state instanceof AvatarRenderState avatar) || JetMech.mechOf(avatar) == null);
   }

   @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
   private AABB specter$mechCulling(AABB box, LivingEntity entity, float partialTicks) {
      return JetMech.cullBox(entity, box);
   }
}
