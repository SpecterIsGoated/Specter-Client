package com.jfant.specter.core.mixin;

import com.jfant.specter.core.renderer.MeshUniforms;
import com.jfant.specter.core.utils.render.postprocess.OutlineUniforms;
import com.jfant.specter.core.utils.render.postprocess.PostProcessShader;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({RenderSystem.class})
public abstract class RenderSystemMixin {
   @Inject(
      method = {"flipFrame"},
      at = {@At("TAIL")}
   )
   private static void meteor$flipFrame(CallbackInfo info) {
      MeshUniforms.flipFrame();
      PostProcessShader.flipFrame();
      OutlineUniforms.flipFrame();
   }
}
