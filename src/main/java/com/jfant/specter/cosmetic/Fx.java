package com.jfant.specter.cosmetic;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3fc;

/** Colour maths, easing, light sampling and the camera basis shared by every cosmetic. */
public final class Fx {
   /** Face shading like vanilla blocks: down, up, north, south, west, east. */
   public static final float[] BLOCK_SHADE = new float[]{0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
   private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

   /** Camera right / up / forward for this frame (filled by {@link #camera()}). */
   public static double rightX, rightY, rightZ, upX, upY, upZ, fwdX, fwdY, fwdZ, camX, camY, camZ;

   private Fx() {
   }

   /** Reads the camera basis. Call once per render event before drawing camera-facing things. */
   public static void camera() {
      Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
      Vector3fc left = camera.leftVector();
      Vector3fc up = camera.upVector();
      Vector3fc fwd = camera.forwardVector();
      rightX = -left.x();
      rightY = -left.y();
      rightZ = -left.z();
      upX = up.x();
      upY = up.y();
      upZ = up.z();
      fwdX = fwd.x();
      fwdY = fwd.y();
      fwdZ = fwd.z();
      camX = camera.position().x;
      camY = camera.position().y;
      camZ = camera.position().z;
   }

   public static int argb(int a, int r, int g, int b) {
      return (Mth.clamp(a, 0, 255) << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
   }

   public static int withAlpha(int argb, float alpha) {
      int a = Math.round((argb >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F));
      return a << 24 | (argb & 0xFFFFFF);
   }

   public static int alpha(int rgb, int alpha) {
      return Mth.clamp(alpha, 0, 255) << 24 | (rgb & 0xFFFFFF);
   }

   /** Multiplies the RGB (not alpha) by {@code f}. */
   public static int shade(int argb, float f) {
      int r = Math.min(255, Math.round((argb >> 16 & 255) * f));
      int g = Math.min(255, Math.round((argb >> 8 & 255) * f));
      int b = Math.min(255, Math.round((argb & 255) * f));
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static int lerp(int a, int b, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
      int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
      return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   /** Hue 0-1 to an opaque RGB colour. */
   public static int hsv(float h, float s, float v) {
      return 0xFF000000 | Mth.hsvToRgb(((h % 1.0F) + 1.0F) % 1.0F, s, v);
   }

   public static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   public static float easeOutBack(float t) {
      float c1 = 1.70158F, c3 = c1 + 1.0F;
      float u = t - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   /** 0..1 brightness of a spot like the world shades it (sky light scaled by daylight, block light). */
   public static float light(Level level, double x, double y, double z) {
      if (level == null) return 1.0F;
      POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
      int block = level.getBrightness(LightLayer.BLOCK, POS);
      int sky = level.getBrightness(LightLayer.SKY, POS) - level.getSkyDarken();
      float l = Math.max(block, Math.max(0, sky)) / 15.0F;
      return 0.28F + 0.72F * l;
   }

   /** Smoothly moves {@code current} towards {@code target}; {@code rate} is per second, frame-rate independent. */
   public static float approach(float current, float target, float rate, float dtSeconds) {
      return current + (target - current) * (1.0F - (float)Math.exp(-rate * dtSeconds));
   }
}
