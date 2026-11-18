package com.jfant.specter.festive;

import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.render.QuadBatch;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

public final class Snowfield {
   private static final float AREA_PER_FLAKE = 2400.0F;
   private static final int MAX_FLAKES = 900;
   private final Random random = new Random(99384850L);
   private float[] x = new float[0];
   private float[] y;
   private float[] depth;
   private float[] phase;
   private float[] rate;
   private float[] swing;
   private float[] spin;
   private int count;
   private float width;
   private float height;
   private double wind;
   private double gustClock;
   private double lastSeen = -1.0;
   private final float[] clumpX = new float[96];
   private final float[] clumpY = new float[96];
   private final float[] clumpVx = new float[96];
   private final float[] clumpVy = new float[96];
   private final float[] clumpLife = new float[96];
   private int clumpNext;

   public void draw(GuiGraphics ctx, float width, float height, float density, float speed, float windBias, float size, float alpha) {
      if (!(alpha <= 0.01F) && !(width <= 0.0F) && !(height <= 0.0F)) {
         this.resize(width, height, density);
         this.step(speed, windBias);
         QuadBatch b = QuadBatch.colored(ctx);

         for (int i = 0; i < this.count; i++) {
            float d = this.depth[i];
            float glint = 0.82F + 0.18F * (float)Math.sin(this.spin[i] + this.phase[i] * 3.0F);
            int a = Math.round(Math.min(1.0F, (0.28F + 0.72F * d) * glint * alpha) * 255.0F);
            if (a > 3) {
               flake(b, this.x[i], this.y[i], d, size, a, this.spin[i]);
            }
         }

         for (int ix = 0; ix < this.clumpLife.length; ix++) {
            if (!(this.clumpLife[ix] <= 0.0F)) {
               int a = Math.round(Math.min(1.0F, this.clumpLife[ix]) * alpha * 235.0F);
               float s = 1.0F + this.clumpLife[ix];
               b.fill(this.clumpX[ix], this.clumpY[ix], this.clumpX[ix] + s, this.clumpY[ix] + s, a << 24 | 16777215);
            }
         }

         b.submit(ctx);
      }
   }

   public void shed(float x, float y, float push) {
      int i = this.clumpNext++ % this.clumpLife.length;
      this.clumpX[i] = x;
      this.clumpY[i] = y;
      this.clumpVx[i] = push + (this.random.nextFloat() - 0.5F) * 26.0F;
      this.clumpVy[i] = -8.0F - this.random.nextFloat() * 16.0F;
      this.clumpLife[i] = 0.7F + this.random.nextFloat() * 0.6F;
   }

   private void resize(float w, float h, float density) {
      int wanted = (int)Math.min(900.0F, w * h / 2400.0F * Math.max(0.0F, density));
      boolean reshaped = Math.abs(w - this.width) > 1.0F || Math.abs(h - this.height) > 1.0F;
      this.width = w;
      this.height = h;
      if (wanted != this.count || reshaped) {
         if (wanted > this.x.length) {
            this.x = grow(this.x, wanted);
            this.y = grow(this.y, wanted);
            this.depth = grow(this.depth, wanted);
            this.phase = grow(this.phase, wanted);
            this.rate = grow(this.rate, wanted);
            this.swing = grow(this.swing, wanted);
            this.spin = grow(this.spin, wanted);
         }

         for (int i = reshaped ? 0 : this.count; i < wanted; i++) {
            this.spawn(i, true);
         }

         this.count = wanted;
      }
   }

   private static float[] grow(float[] old, int size) {
      float[] out = new float[Math.max(size, 64)];
      if (old != null) {
         System.arraycopy(old, 0, out, 0, old.length);
      }

      return out;
   }

   private void spawn(int i, boolean anywhere) {
      float roll = this.random.nextFloat();
      this.depth[i] = roll * roll;
      this.x[i] = this.random.nextFloat() * (this.width + 40.0F) - 20.0F;
      this.y[i] = anywhere ? this.random.nextFloat() * this.height : -6.0F - this.random.nextFloat() * 20.0F;
      this.phase[i] = this.random.nextFloat() * 6.2832F;
      this.rate[i] = 0.7F + this.random.nextFloat() * 1.1F;
      this.swing[i] = 4.0F + this.random.nextFloat() * 10.0F;
      this.spin[i] = this.random.nextFloat() * 6.2832F;
   }

   private void step(float speed, float windBias) {
      double now = Motion.now();
      float dt = !(this.lastSeen < 0.0) && !(now - this.lastSeen > 0.25) ? (float)(now - this.lastSeen) : 0.0F;
      this.lastSeen = now;
      if (!Motion.plays(Channel.FESTIVE)) {
         dt = 0.0F;
      }

      if (!(dt <= 0.0F)) {
         this.gustClock += dt;
         this.wind = windBias * 26.0F + Math.sin(this.gustClock * 0.21) * 9.0 + Math.sin(this.gustClock * 0.057 + 1.3) * 7.0;

         for (int i = 0; i < this.count; i++) {
            float d = this.depth[i];
            this.phase[i] = this.phase[i] + this.rate[i] * dt;
            this.spin[i] = this.spin[i] + (0.6F + d) * dt;
            float sway = (float)Math.cos(this.phase[i]);
            float fall = (11.0F + 34.0F * d) * speed * (0.78F + 0.22F * Math.abs(sway));
            this.y[i] = this.y[i] + fall * dt;
            this.x[i] = this.x[i] + (sway * this.swing[i] * this.rate[i] * (0.35F + 0.65F * d) + (float)this.wind * (0.3F + 0.7F * d)) * dt;
            if (this.y[i] > this.height + 6.0F) {
               this.spawn(i, false);
            } else if (this.x[i] < -24.0F) {
               this.x[i] = this.x[i] + (this.width + 44.0F);
            } else if (this.x[i] > this.width + 24.0F) {
               this.x[i] = this.x[i] - (this.width + 44.0F);
            }
         }

         for (int ix = 0; ix < this.clumpLife.length; ix++) {
            if (!(this.clumpLife[ix] <= 0.0F)) {
               this.clumpLife[ix] = this.clumpLife[ix] - dt * 0.9F;
               this.clumpVy[ix] = this.clumpVy[ix] + 170.0F * dt;
               this.clumpVx[ix] = this.clumpVx[ix] + ((float)this.wind - this.clumpVx[ix]) * dt * 0.8F;
               this.clumpX[ix] = this.clumpX[ix] + this.clumpVx[ix] * dt;
               this.clumpY[ix] = this.clumpY[ix] + this.clumpVy[ix] * dt;
            }
         }
      }
   }

   private static void flake(QuadBatch b, float x, float y, float depth, float size, int alpha, float spin) {
      int white = alpha << 24 | 16777215;
      if (depth < 0.34F) {
         b.fill(x, y, x + size, y + size, white);
      } else if (depth < 0.62F) {
         b.fill(x, y, x + 1.6F * size, y + 1.6F * size, white);
      } else if (depth < 0.86F) {
         int arm = alpha * 150 / 255 << 24 | 16777215;
         b.fill(x, y, x + size, y + size, white);
         b.fill(x - size, y, x, y + size, arm);
         b.fill(x + size, y, x + 2.0F * size, y + size, arm);
         b.fill(x, y - size, x + size, y, arm);
         b.fill(x, y + size, x + size, y + 2.0F * size, arm);
      } else {
         float turn = 0.5F + 0.5F * (float)Math.sin(spin * 2.0F);
         int straight = Math.round(alpha * (0.55F + 0.45F * turn)) << 24 | 16777215;
         int diagonal = Math.round(alpha * (0.55F + 0.45F * (1.0F - turn)) * 0.7F) << 24 | 16777215;
         b.fill(x, y, x + size, y + size, white);
         b.fill(x - 2.0F * size, y, x, y + size, straight);
         b.fill(x + size, y, x + 3.0F * size, y + size, straight);
         b.fill(x, y - 2.0F * size, x + size, y, straight);
         b.fill(x, y + size, x + size, y + 3.0F * size, straight);
         b.fill(x - size, y - size, x, y, diagonal);
         b.fill(x + size, y - size, x + 2.0F * size, y, diagonal);
         b.fill(x - size, y + size, x, y + 2.0F * size, diagonal);
         b.fill(x + size, y + size, x + 2.0F * size, y + 2.0F * size, diagonal);
      }
   }
}
