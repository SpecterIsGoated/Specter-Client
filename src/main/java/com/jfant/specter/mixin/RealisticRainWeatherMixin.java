package com.jfant.specter.mixin;

import com.jfant.specter.modules.RealisticRain;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realistic Rain: white rain streaks (own texture, vanilla sampler) lit as brightly as snow. Snow is untouched. */
@Mixin(WeatherEffectRenderer.class)
public abstract class RealisticRainWeatherMixin {
   @Shadow
   private AbstractTexture rainTexture;

   @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
   private void specter$whiteRain(RenderPass renderPass, AbstractTexture texture, int startColumn, int columnCount, CallbackInfo ci) {
      if (texture != null && texture == this.rainTexture && columnCount > 0) {
         GpuTextureView white = RealisticRain.whiteRainView();
         if (white != null) {
            renderPass.setUniform("Sampler0", white, texture.getSampler());
            renderPass.drawIndexed(columnCount * 6, 1, startColumn * 6, 0, 0);
            ci.cancel();
         }
      }
   }

   /** Less rain: only a fixed share of rain columns (picked by position, so it does not flicker) are drawn. */
   @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
   private boolean specter$fewerRainColumns(List<Object> columns, Object column, Operation<Boolean> original) {
      if (column instanceof WeatherEffectRenderer.ColumnInstance c && !RealisticRain.keepRainColumn(c.x(), c.z())) {
         return false;
      }

      return original.call(columns, column);
   }

   @ModifyArg(
      method = "createRainColumnInstance",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer$ColumnInstance;<init>(IIIIFFI)V"),
      index = 6
   )
   private int specter$brightRain(int lightCoords) {
      if (!RealisticRain.brightRain()) {
         return lightCoords;
      }

      return LightCoordsUtil.pack((LightCoordsUtil.block(lightCoords) * 3 + 15) / 4, (LightCoordsUtil.sky(lightCoords) * 3 + 15) / 4);
   }
}
