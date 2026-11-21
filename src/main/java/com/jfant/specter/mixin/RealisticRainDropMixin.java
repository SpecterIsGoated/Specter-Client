package com.jfant.specter.mixin;

import com.jfant.specter.modules.RealisticRain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realistic Rain: a weather splash spawned while the white flag is up gets a small white droplet instead of the blue one. */
@Mixin(WaterDropParticle.class)
public abstract class RealisticRainDropMixin extends SingleQuadParticle {
   protected RealisticRainDropMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
      super(level, x, y, z, sprite);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void specter$whiteDrop(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, CallbackInfo ci) {
      if (RealisticRain.spawningWhiteSplash) {
         TextureAtlasSprite white = RealisticRain.whiteDropSprite();
         if (white != null) {
            this.setSprite(white);
            this.setColor(0.95F, 0.97F, 1.0F);
            this.quadSize *= 0.6F;
         }
      }
   }
}
