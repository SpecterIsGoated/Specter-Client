package com.jfant.specter.staff;

import com.jfant.specter.render.CardText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

public final class WatchingAlert {
   private static final long HEAD_IN = 500L;
   private static final long TEXT_AT = 600L;
   private static final long TEXT_IN = 500L;
   private static final long OUT_AT = 4500L;
   private static final long OUT = 600L;
   private static final long PULSE = 1100L;
   private static final int HEAD = 48;
   private static final float TEXT_SCALE = 2.5F;
   private static final int GAP = 8;
   private static final float DRIFT = 12.0F;
   private static final int RAISE = 70;
   private static final int RED = 16726843;
   private static final int RED_BRIGHT = 16743034;
   private final Minecraft mc = Minecraft.getInstance();
   private String title;
   private PlayerSkin skin;
   private long start;
   private boolean active;
   private boolean textSounded;
   private boolean loud;

   public void play(String name, PlayerSkin skin, int others, boolean sound) {
      this.title = (others > 0 ? name + " +" + others : name) + " Is Watching You";
      this.skin = skin != null ? skin : DefaultPlayerSkin.getDefaultSkin();
      this.start = System.currentTimeMillis();
      this.active = true;
      this.textSounded = false;
      this.loud = sound;
      if (this.loud) {
         this.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.9F);
      }
   }

   public boolean active() {
      return this.active;
   }

   public void stop() {
      this.active = false;
   }

   public void render(GuiGraphicsExtractor ctx) {
      if (this.active) {
         long t = System.currentTimeMillis() - this.start;
         if (t >= 5100L) {
            this.active = false;
         } else {
            if (!this.textSounded && t >= 600L) {
               this.textSounded = true;
               if (this.loud) {
                  this.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F);
               }
            }

            float out = t < 4500L ? 0.0F : ease((float)(t - 4500L) / 600.0F);
            float fadeOut = 1.0F - out;
            float lift = 12.0F * out;
            int centerX = ctx.guiWidth() / 2;
            float top = ctx.guiHeight() / 2.0F - 70.0F - lift;
            float headIn = ease(Math.min(1.0F, (float)t / 500.0F));
            float headAlpha = headIn * fadeOut;
            if (headAlpha > 0.01F) {
               float size = 48.0F * (1.12F - 0.12F * headIn);
               Matrix3x2fStack m = ctx.pose();
               m.pushMatrix();
               m.translate(centerX, top + 24.0F);
               m.scale(size / 48.0F, size / 48.0F);
               PlayerFaceExtractor.extractRenderState(ctx, this.skin, -24, -24, 48, alpha(headAlpha) | 16777215);
               m.popMatrix();
            }

            if (t >= 600L) {
               float textIn = ease(Math.min(1.0F, (float)(t - 600L) / 500.0F));
               float textAlpha = textIn * fadeOut;
               if (textAlpha > 0.01F) {
                  double phase = (t - 600L) % 1100L / 1100.0 * 2.0 * 3.141592653589793;
                  float glow = (float)(0.5 - 0.5 * Math.cos(phase)) * textIn;
                  int rgb = lerpRgb(16726843, 16743034, glow * 0.6F);
                  int width = CardText.width(this.title);
                  float textY = top + 48.0F + 8.0F + (1.0F - textIn) * 4.0F;
                  Matrix3x2fStack m = ctx.pose();
                  m.pushMatrix();
                  m.translate(centerX - width * 2.5F / 2.0F, textY);
                  m.scale(2.5F, 2.5F);
                  CardText.draw(ctx, this.title, 0.0F, 0.0F, alpha(textAlpha) | rgb, true);
                  m.popMatrix();
               }
            }
         }
      }
   }

   private void playSound(SoundEvent event, float pitch) {
      this.mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, 1.0F));
   }

   private static float ease(float x) {
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   private static int alpha(float a) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24;
   }

   private static int lerpRgb(int from, int to, float t) {
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return r << 16 | g << 8 | b;
   }
}
