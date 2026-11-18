package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.ArmRenderEvent;
import com.jfant.specter.core.events.render.HeldItemRendererEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class HeldItemRendererMixin {
   @Shadow
   private float mainHandHeight;
   @Shadow
   private float offHandHeight;
   @Shadow
   private ItemStack mainHandItem;
   @Shadow
   private ItemStack offHandItem;

   @Shadow
   protected abstract boolean shouldInstantlyReplaceVisibleItem(ItemStack var1, ItemStack var2);

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
         shift = Shift.BEFORE
      )}
   )
   private void onRenderItem(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector orderedRenderCommandQueue,
      int light,
      CallbackInfo ci
   ) {
      SpecterCore.EVENT_BUS.post(HeldItemRendererEvent.get(hand, matrices));
   }

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"
      )}
   )
   private void onRenderArm(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector orderedRenderCommandQueue,
      int light,
      CallbackInfo ci
   ) {
      SpecterCore.EVENT_BUS.post(ArmRenderEvent.get(hand, matrices));
   }
}
