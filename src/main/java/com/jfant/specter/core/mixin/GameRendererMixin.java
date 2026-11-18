package com.jfant.specter.core.mixin;

import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.GetFovEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.render.RenderAfterWorldEvent;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.mixininterface.IGameRenderer;
import com.jfant.specter.core.renderer.MeteorRenderPipelines;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.CustomBannerGuiElementRenderer;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.util.profiling.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin implements IGameRenderer {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   @Final
   private Camera mainCamera;
   @Unique
   private Renderer3D renderer;
   @Unique
   private Renderer3D depthRenderer;
   @Unique
   private final PoseStack matrices = new PoseStack();
   @Shadow
   @Final
   private RenderBuffers renderBuffers;
   @Shadow
   @Final
   private GuiRenderer guiRenderer;
   @Shadow
   @Final
   private FogRenderer fogRenderer;
   @Shadow
   @Final
   GuiRenderState guiRenderState;

   @Shadow
   public abstract void pick(float var1);

   @Shadow
   public abstract void resetData();

   @Shadow
   protected abstract void bobView(PoseStack var1, float var2);

   @Shadow
   protected abstract void bobHurt(PoseStack var1, float var2);

   @ModifyArg(
      method = {"<init>"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/gui/render/state/GuiRenderState;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"
      )
   )
   private List<PictureInPictureRenderer<?>> meteor$addSpecialRenderers(List<PictureInPictureRenderer<?>> list) {
      List<PictureInPictureRenderer<?>> var2 = new ArrayList<>(list);
      var2.add(new CustomBannerGuiElementRenderer(this.renderBuffers.bufferSource(), this.minecraft.getAtlasManager()));
      return List.of(var2.toArray(new PictureInPictureRenderer[0]));
   }

   @Inject(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE_STRING",
         target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
         args = {"ldc=hand"}
      )}
   )
   private void onRenderWorld(
      DeltaTracker tickCounter,
      CallbackInfo ci,
      @Local(ordinal = 0) Matrix4f projection,
      @Local(ordinal = 1) Matrix4f position,
      @Local(ordinal = 0) float tickDelta,
      @Local PoseStack matrixStack
   ) {
      if (Utils.canUpdate()) {
         Profiler.get().push("specter_render");
         if (this.renderer == null) {
            this.renderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES, MeteorRenderPipelines.WORLD_COLORED);
         }

         if (this.depthRenderer == null) {
            this.depthRenderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES_DEPTH, MeteorRenderPipelines.WORLD_COLORED_DEPTH);
         }

         Render3DEvent event = Render3DEvent.get(
            matrixStack, this.renderer, this.depthRenderer, tickDelta, this.mainCamera.position().x, this.mainCamera.position().y, this.mainCamera.position().z
         );
         RenderSystem.getModelViewStack().pushMatrix().mul(position);
         this.matrices.pushPose();
         this.bobHurt(this.matrices, this.mainCamera.getPartialTickTime());
         if (this.minecraft.options.bobView().get()) {
            this.bobView(this.matrices, this.mainCamera.getPartialTickTime());
         }

         Matrix4f inverseBob = new Matrix4f(this.matrices.last().pose()).invert();
         RenderSystem.getModelViewStack().mul(inverseBob);
         this.matrices.popPose();
         Matrix4f correctedPosition = MixinPlugin.isIrisPresent && RenderUtils.isShaderPackInUse() ? new Matrix4f(position).mul(inverseBob) : position;
         RenderUtils.updateScreenCenter(projection, correctedPosition);
         NametagUtils.onRender(position);
         this.renderer.begin();
         this.depthRenderer.begin();
         SpecterCore.EVENT_BUS.post(event);
         this.renderer.render(matrixStack);
         this.depthRenderer.render(matrixStack);
         RenderSystem.getModelViewStack().popMatrix();
         Profiler.get().pop();
      }
   }

   @Inject(
      method = {"renderLevel"},
      at = {@At("TAIL")}
   )
   private void onRenderWorldTail(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(RenderAfterWorldEvent.get());
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
         shift = Shift.AFTER
      )}
   )
   private void onRenderGui(DeltaTracker tickCounter, boolean tick, CallbackInfo info) {
      if (this.minecraft.screen instanceof WidgetScreen widgetScreen) {
         this.guiRenderState.reset();
         int mouseX = (int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
         int mouseY = (int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
         GuiGraphics context = new GuiGraphics(this.minecraft, this.guiRenderState, mouseX, mouseY);
         widgetScreen.renderCustom(context, mouseX, mouseY, tickCounter.getGameTimeDeltaTicks());
         RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(this.minecraft.getMainRenderTarget().getDepthTexture(), 1.0);
         this.meteor$flushGuiState();
      }
   }

   @ModifyReturnValue(
      method = {"getFov"},
      at = {@At("RETURN")}
   )
   private float modifyFov(float original) {
      return ((GetFovEvent)SpecterCore.EVENT_BUS.post(GetFovEvent.get(original))).fov;
   }

   @Override
   public void meteor$flushGuiState() {
      this.guiRenderer.render(this.fogRenderer.getBuffer(FogMode.NONE));
      this.guiRenderer.incrementFrameNumber();
   }
}
