package com.jfant.specter.mixin;

import com.jfant.specter.fx.WorldFx;
import com.jfant.specter.modules.SkyLyrics;
import com.jfant.specter.sky.SkyRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({RenderTypes.class})
public abstract class ImmediateLayerMixin {
   @Inject(
      method = {"waterMask"},
      at = {@At("HEAD")}
   )
   private static void specter$beforeWater(CallbackInfoReturnable<RenderType> cir) {
      SkyLyrics.captureSolidDepth();
      SkyRenderer.afterSolidTerrain();
      WorldFx.captureSolidDepth();
   }
}
