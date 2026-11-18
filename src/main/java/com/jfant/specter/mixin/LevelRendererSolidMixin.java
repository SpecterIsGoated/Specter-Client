package com.jfant.specter.mixin;

import com.jfant.specter.render.SolidWorldHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x: the solid world and (without improved transparency) the see-through world are drawn in one render pass, and
 * nothing else may touch the GPU while a pass is open. When Specter needs the "solid world done" moment
 * ({@link SolidWorldHooks}), the classic transparency step is held back, the pass closes, Specter's hooks run, and the
 * held-back step is replayed in a fresh pass on the same target. With improved transparency the solid pass already
 * closes before the OIT passes, so the hooks simply run there.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSolidMixin {
   @Unique
   private ChunkSectionsToRender specter$heldSections;
   @Unique
   private FeatureRenderDispatcher.PreparedFrame specter$heldFrame;

   @Shadow
   protected abstract void executeClassicTransparency(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass);

   @WrapOperation(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
      )
   )
   private void specter$holdTransparency(
      LevelRenderer self, ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, Operation<Void> original
   ) {
      if (SolidWorldHooks.wanted()) {
         this.specter$heldSections = sections;
         this.specter$heldFrame = frame;
      } else {
         original.call(self, sections, frame, pass);
      }
   }

   @Inject(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOit(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"
      )
   )
   private void specter$beforeOit(CallbackInfo ci) {
      if (SolidWorldHooks.wanted()) {
         SolidWorldHooks.run();
      }
   }

   @Inject(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"
      )
   )
   private void specter$replayTransparency(CallbackInfo ci) {
      ChunkSectionsToRender sections = this.specter$heldSections;
      FeatureRenderDispatcher.PreparedFrame frame = this.specter$heldFrame;
      if (sections == null || frame == null) {
         return;
      }

      this.specter$heldSections = null;
      this.specter$heldFrame = null;
      SolidWorldHooks.run();
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();

      try (RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Main (translucent)", main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
         RenderSystem.bindDefaultUniforms(pass);
         this.executeClassicTransparency(sections, frame, pass);
      }
   }
}
