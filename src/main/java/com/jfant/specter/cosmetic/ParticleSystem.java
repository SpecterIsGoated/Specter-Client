package com.jfant.specter.cosmetic;

import com.jfant.specter.core.renderer.Renderer3D;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fixed-size particle pool shared by cosmetics: movement (velocity, gravity, drag, floor bounce/landing), rotation
 * (billboard spin or a full 3D tumble), size and colour over life, fade in/out, glow, and lifetime. Everything lives in
 * flat arrays (no objects per particle, nothing allocated while running); dead particles are swapped out.
 *
 * <p>Ticked 20 times a second, drawn every frame with the previous/current positions interpolated, so it stays smooth
 * at any frame rate. Shapes come from {@link Shapes} (see {@link #SHAPE_CUBE} for voxel particles).
 */
public final class ParticleSystem {
   public static final int BILLBOARD = 1;
   public static final int GLOW = 2;
   public static final int FLOOR = 4;
   /** Body drawn in the solid renderer (opaque, sorts correctly). Otherwise translucent. */
   public static final int SOLID = 8;
   /** Body drawn additively (sparks, embers). */
   public static final int ADDITIVE = 16;
   /** Lands and stays on the floor instead of bouncing (puddles, petals). */
   public static final int SETTLE = 32;
   /** Shrinks to nothing over its life instead of using size1. */
   public static final int SHRINK = 64;
   public static final int SHAPE_CUBE = -1;

   private final int capacity;
   private int count;
   private final float[] x, y, z, ox, oy, oz, vx, vy, vz;
   private final float[] spin, ospin, spinV, yaw, oyaw, yawV, tilt, otilt, tiltV;
   private final float[] size0, size1, gravity, drag, bounce;
   private final int[] age, life, col0, col1, glowCol, shape, flags;
   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

   /** What the next {@link #spawn} uses. Set the fields, then call spawn as often as needed. */
   public final Spec spec = new Spec();

   public static final class Spec {
      public int shape = Shapes.ID_DOT;
      public int flags = BILLBOARD;
      public int life = 20;
      public float size0 = 0.1F;
      public float size1 = 0.1F;
      public int color0 = 0xFFFFFFFF;
      public int color1 = 0x00FFFFFF;
      public int glow = 0;
      public float gravity = 0.0F;
      public float drag = 0.98F;
      public float bounce = 0.3F;
      public float spin = 0.0F;
      public float spinV = 0.0F;
      public float yaw = 0.0F;
      public float yawV = 0.0F;
      public float tilt = 0.0F;
      public float tiltV = 0.0F;

      public Spec reset() {
         this.shape = Shapes.ID_DOT;
         this.flags = BILLBOARD;
         this.life = 20;
         this.size0 = this.size1 = 0.1F;
         this.color0 = 0xFFFFFFFF;
         this.color1 = 0x00FFFFFF;
         this.glow = 0;
         this.gravity = 0.0F;
         this.drag = 0.98F;
         this.bounce = 0.3F;
         this.spin = this.spinV = this.yaw = this.yawV = this.tilt = this.tiltV = 0.0F;
         return this;
      }
   }

   public ParticleSystem(int capacity) {
      this.capacity = capacity;
      this.x = new float[capacity];
      this.y = new float[capacity];
      this.z = new float[capacity];
      this.ox = new float[capacity];
      this.oy = new float[capacity];
      this.oz = new float[capacity];
      this.vx = new float[capacity];
      this.vy = new float[capacity];
      this.vz = new float[capacity];
      this.spin = new float[capacity];
      this.ospin = new float[capacity];
      this.spinV = new float[capacity];
      this.yaw = new float[capacity];
      this.oyaw = new float[capacity];
      this.yawV = new float[capacity];
      this.tilt = new float[capacity];
      this.otilt = new float[capacity];
      this.tiltV = new float[capacity];
      this.size0 = new float[capacity];
      this.size1 = new float[capacity];
      this.gravity = new float[capacity];
      this.drag = new float[capacity];
      this.bounce = new float[capacity];
      this.age = new int[capacity];
      this.life = new int[capacity];
      this.col0 = new int[capacity];
      this.col1 = new int[capacity];
      this.glowCol = new int[capacity];
      this.shape = new int[capacity];
      this.flags = new int[capacity];
   }

   public int count() {
      return this.count;
   }

   public int capacity() {
      return this.capacity;
   }

   public void clear() {
      this.count = 0;
   }

   /**
    * Positions are stored as floats relative to an origin so world coordinates in the millions keep sub-block
    * precision; the origin follows the first spawn after the pool empties.
    */
   private double originX, originY, originZ;

   /** Spawns one particle from {@link #spec}; returns false when the pool is full. */
   public boolean spawn(double wx, double wy, double wz, double velX, double velY, double velZ) {
      if (this.count >= this.capacity) return false;
      if (this.count == 0) {
         this.originX = Math.floor(wx);
         this.originY = Math.floor(wy);
         this.originZ = Math.floor(wz);
      }
      int i = this.count++;
      Spec s = this.spec;
      this.x[i] = this.ox[i] = (float)(wx - this.originX);
      this.y[i] = this.oy[i] = (float)(wy - this.originY);
      this.z[i] = this.oz[i] = (float)(wz - this.originZ);
      this.vx[i] = (float)velX;
      this.vy[i] = (float)velY;
      this.vz[i] = (float)velZ;
      this.spin[i] = this.ospin[i] = s.spin;
      this.spinV[i] = s.spinV;
      this.yaw[i] = this.oyaw[i] = s.yaw;
      this.yawV[i] = s.yawV;
      this.tilt[i] = this.otilt[i] = s.tilt;
      this.tiltV[i] = s.tiltV;
      this.size0[i] = s.size0;
      this.size1[i] = s.size1;
      this.gravity[i] = s.gravity;
      this.drag[i] = s.drag;
      this.bounce[i] = s.bounce;
      this.age[i] = 0;
      this.life[i] = Math.max(1, s.life);
      this.col0[i] = s.color0;
      this.col1[i] = s.color1;
      this.glowCol[i] = s.glow;
      this.shape[i] = s.shape;
      this.flags[i] = s.flags;
      return true;
   }

   /** One game tick: ages, moves and collides every particle. */
   public void tick(Level level) {
      for (int i = 0; i < this.count; i++) {
         if (++this.age[i] >= this.life[i]) {
            this.remove(i--);
            continue;
         }
         this.ox[i] = this.x[i];
         this.oy[i] = this.y[i];
         this.oz[i] = this.z[i];
         this.ospin[i] = this.spin[i];
         this.oyaw[i] = this.yaw[i];
         this.otilt[i] = this.tilt[i];
         this.vy[i] -= this.gravity[i];
         float d = this.drag[i];
         this.vx[i] *= d;
         this.vy[i] *= d;
         this.vz[i] *= d;
         float nx = this.x[i] + this.vx[i];
         float ny = this.y[i] + this.vy[i];
         float nz = this.z[i] + this.vz[i];
         if ((this.flags[i] & FLOOR) != 0 && level != null && this.vy[i] < 0.0F && this.solidAt(level, nx, ny, nz)) {
            float top = (float)(Math.floor(ny + this.originY) + 1.0 - this.originY) + 0.01F;
            ny = top;
            if ((this.flags[i] & SETTLE) != 0) {
               this.vy[i] = 0.0F;
               this.vx[i] *= 0.4F;
               this.vz[i] *= 0.4F;
               this.gravity[i] = 0.0F;
               this.spinV[i] *= 0.2F;
               this.tiltV[i] = 0.0F;
            } else {
               this.vy[i] = -this.vy[i] * this.bounce[i];
               this.vx[i] *= 0.7F;
               this.vz[i] *= 0.7F;
               this.spinV[i] *= 0.6F;
            }
         }
         this.x[i] = nx;
         this.y[i] = ny;
         this.z[i] = nz;
         this.spin[i] += this.spinV[i];
         this.yaw[i] += this.yawV[i];
         this.tilt[i] += this.tiltV[i];
      }
   }

   private boolean solidAt(Level level, float lx, float ly, float lz) {
      this.pos.set(Mth.floor(lx + this.originX), Mth.floor(ly + this.originY), Mth.floor(lz + this.originZ));
      if (!level.isLoaded(this.pos)) return false;
      BlockState state = level.getBlockState(this.pos);
      return state.isSolid() || !state.getFluidState().isEmpty();
   }

   private void remove(int i) {
      int last = --this.count;
      if (i == last) return;
      this.x[i] = this.x[last];
      this.y[i] = this.y[last];
      this.z[i] = this.z[last];
      this.ox[i] = this.ox[last];
      this.oy[i] = this.oy[last];
      this.oz[i] = this.oz[last];
      this.vx[i] = this.vx[last];
      this.vy[i] = this.vy[last];
      this.vz[i] = this.vz[last];
      this.spin[i] = this.spin[last];
      this.ospin[i] = this.ospin[last];
      this.spinV[i] = this.spinV[last];
      this.yaw[i] = this.yaw[last];
      this.oyaw[i] = this.oyaw[last];
      this.yawV[i] = this.yawV[last];
      this.tilt[i] = this.tilt[last];
      this.otilt[i] = this.otilt[last];
      this.tiltV[i] = this.tiltV[last];
      this.size0[i] = this.size0[last];
      this.size1[i] = this.size1[last];
      this.gravity[i] = this.gravity[last];
      this.drag[i] = this.drag[last];
      this.bounce[i] = this.bounce[last];
      this.age[i] = this.age[last];
      this.life[i] = this.life[last];
      this.col0[i] = this.col0[last];
      this.col1[i] = this.col1[last];
      this.glowCol[i] = this.glowCol[last];
      this.shape[i] = this.shape[last];
      this.flags[i] = this.flags[last];
   }

   /**
    * Draws every particle. {@link Fx#camera()} must have been called this frame. {@code glowScale} scales the glow
    * discs of GLOW particles (0 hides them).
    */
   public void render(Renderer3D solid, Renderer3D translucent, Renderer3D glow, float pt, float glowScale) {
      for (int i = 0; i < this.count; i++) {
         float t = (this.age[i] + pt) / this.life[i];
         if (t >= 1.0F) continue;
         double px = this.originX + Mth.lerp(pt, this.ox[i], this.x[i]);
         double py = this.originY + Mth.lerp(pt, this.oy[i], this.y[i]);
         double pz = this.originZ + Mth.lerp(pt, this.oz[i], this.z[i]);
         int f = this.flags[i];
         float size = (f & SHRINK) != 0 ? this.size0[i] * (1.0F - t) : Mth.lerp(t, this.size0[i], this.size1[i]);
         if (size <= 0.002F) continue;
         // fade in over the first 8%, out over the last 35%
         float fade = Math.min(1.0F, t / 0.08F) * Math.min(1.0F, (1.0F - t) / 0.35F);
         int color = Fx.lerp(this.col0[i], this.col1[i] | (this.col0[i] & 0xFF000000), t);
         color = Fx.withAlpha(color, fade);
         Renderer3D body = (f & ADDITIVE) != 0 ? glow : ((f & SOLID) != 0 ? solid : translucent);
         if (this.shape[i] == SHAPE_CUBE) {
            double h = size * 0.5;
            Draw3D.boxFacing(body, px - h, py - h, pz - h, px + h, py + h, pz + h, color, Fx.BLOCK_SHADE, Fx.camX, Fx.camY, Fx.camZ);
         } else {
            float[] outline = Shapes.ALL[Mth.clamp(this.shape[i], 0, Shapes.ALL.length - 1)];
            float sp = Mth.lerp(pt, this.ospin[i], this.spin[i]);
            double cs = Math.cos(sp) * size, sn = Math.sin(sp) * size;
            double rx, ry, rz, ux, uy, uz;
            if ((f & BILLBOARD) != 0) {
               rx = Fx.rightX * cs + Fx.upX * sn;
               ry = Fx.rightY * cs + Fx.upY * sn;
               rz = Fx.rightZ * cs + Fx.upZ * sn;
               ux = Fx.upX * cs - Fx.rightX * sn;
               uy = Fx.upY * cs - Fx.rightY * sn;
               uz = Fx.upZ * cs - Fx.rightZ * sn;
            } else {
               // tumble: plane turned by yaw (around up) and tilt (around its own right axis), then spun in-plane
               float a = Mth.lerp(pt, this.oyaw[i], this.yaw[i]);
               float b = Mth.lerp(pt, this.otilt[i], this.tilt[i]);
               double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
               double r0x = ca, r0z = -sa;
               double u0x = sa * sb, u0y = cb, u0z = ca * sb;
               rx = r0x * cs + u0x * sn;
               ry = u0y * sn;
               rz = r0z * cs + u0z * sn;
               ux = u0x * cs - r0x * sn;
               uy = u0y * cs;
               uz = u0z * cs - r0z * sn;
            }
            int edge = (f & ADDITIVE) != 0 ? color & 0x00FFFFFF : Fx.shade(color, 0.82F);
            Draw3D.shape(body, outline, px, py, pz, rx, ry, rz, ux, uy, uz, color, edge);
         }
         if ((f & GLOW) != 0 && glowScale > 0.0F && this.glowCol[i] != 0) {
            double g = size * 2.6 * glowScale;
            Draw3D.glow(glow, px, py, pz, Fx.rightX * g, Fx.rightY * g, Fx.rightZ * g, Fx.upX * g, Fx.upY * g, Fx.upZ * g, Fx.withAlpha(this.glowCol[i], fade));
         }
      }
   }
}
