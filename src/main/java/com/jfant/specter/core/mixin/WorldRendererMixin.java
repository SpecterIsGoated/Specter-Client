package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.IWorldRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class WorldRendererMixin implements IWorldRenderer {
   @Shadow
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Shadow
   @Final
   private EntityRenderDispatcher entityRenderDispatcher;
   @Unique
   private Stack<RenderTarget> framebufferStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> framebufferHandleStack;

   @Inject(
      method = {"checkPoseStack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCheckEmpty(PoseStack matrixStack, CallbackInfo info) {
      info.cancel();
   }

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void init$IWorldRenderer(CallbackInfo info) {
      this.framebufferStack = new ObjectArrayList();
      this.framebufferHandleStack = new ObjectArrayList();
   }

   @Override
   public void meteor$pushEntityOutlineFramebuffer(RenderTarget framebuffer) {
      this.framebufferStack.push(this.entityOutlineTarget);
      this.entityOutlineTarget = framebuffer;
      this.framebufferHandleStack.push(this.targets.entityOutline);
      this.targets.entityOutline = () -> framebuffer;
   }

   @Override
   public void meteor$popEntityOutlineFramebuffer() {
      this.entityOutlineTarget = (RenderTarget)this.framebufferStack.pop();
      this.targets.entityOutline = (ResourceHandle<RenderTarget>)this.framebufferHandleStack.pop();
   }
}
