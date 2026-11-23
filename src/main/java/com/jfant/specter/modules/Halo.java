package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.renderer.MeshBuilder;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.cosmetic.Fx;
import com.jfant.specter.cosmetic.Wearer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * An angel halo over your head: a glowing ring (a real 3D torus, shaded top to bottom) with a soft light band around it.
 * Classic, Double (two counter-turning rings), Radiant (sun rays) or Segmented (broken ring). Static colour, rainbow
 * that runs around the ring, or a breathing pulse. Shown in third person (in first person it would be above the camera).
 */
public class Halo extends Module {
   public enum Design {
      Classic,
      Double,
      Radiant,
      Segmented
   }

   public enum ColorMode {
      Static,
      Rainbow,
      Pulse
   }

   private static final int SEGMENTS = 48;
   private static final int SIDES = 8;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Design> design = this.sgGeneral.add(new EnumSetting.Builder<Design>().name("design").description("Halo shape.").defaultValue(Design.Classic).build());
   private final Setting<ColorMode> colorMode = this.sgGeneral
      .add(
         new EnumSetting.Builder<ColorMode>()
            .name("colour-mode")
            .description("Static: one colour. Rainbow: colours run around the ring. Pulse: the colour breathes brighter and dimmer.")
            .defaultValue(ColorMode.Static)
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("colour")
            .description("Halo colour (Static and Pulse).")
            .defaultValue(new SettingColor(255, 222, 120))
            .visible(() -> this.colorMode.get() != ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> height = this.sgGeneral
      .add(new DoubleSetting.Builder().name("height").description("How far above your head it floats.").defaultValue(0.32).range(0.0, 1.5).sliderRange(0.0, 1.0).build());
   private final Setting<Double> radius = this.sgGeneral
      .add(new DoubleSetting.Builder().name("size").description("Ring radius in blocks.").defaultValue(0.3).range(0.15, 0.8).sliderRange(0.15, 0.6).build());
   private final Setting<Double> thickness = this.sgGeneral
      .add(new DoubleSetting.Builder().name("thickness").description("How thick the ring is.").defaultValue(0.04).range(0.01, 0.12).sliderRange(0.01, 0.1).build());
   private final Setting<Double> spinSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("rotation-speed").description("Turns per second (rainbow and the patterned designs show it).").defaultValue(0.35).range(0.0, 3.0).sliderRange(0.0, 2.0).build());
   private final Setting<Double> glow = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow").description("Strength of the light around the ring. 0 turns it off.").defaultValue(1.0).range(0.0, 2.5).sliderRange(0.0, 2.0).build());
   private final Setting<Boolean> followHead = this.sgGeneral
      .add(new BoolSetting.Builder().name("follow-head").description("Tilt a little as you look up and down.").defaultValue(true).build());

   private float bob;

   public Halo() {
      super(SpecterClientAddon.VISUALS, "halo", "An angel halo floats over your head: static, rainbow or pulsing, in four designs. Only you see it.");
   }

   private int colorAt(double around, double time) {
      int base = this.color.get().getPacked() | 0xFF000000;
      return switch (this.colorMode.get()) {
         case Static -> base;
         case Rainbow -> Fx.hsv((float)(around + time * 0.25), 0.75F, 1.0F);
         case Pulse -> Fx.lerp(Fx.shade(base, 0.55F), Fx.lerp(base, 0xFFFFFFFF, 0.35F), (float)(0.5 + 0.5 * Math.sin(time * 3.0)));
      };
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!Wearer.update(event.tickDelta) || !Wearer.thirdPerson || this.mc.player.isInvisible()) return;
      Fx.camera();
      float pt = event.tickDelta;
      double time = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9;
      Vec3 eye = this.mc.player.getEyePosition(pt);
      this.bob = (float)Math.sin(time * 2.2) * 0.02F;
      double cx = eye.x, cy = eye.y + 0.28 + this.height.get() + this.bob, cz = eye.z;
      // ring plane: horizontal, optionally tipped forward/back with the head
      double headYaw = Math.toRadians(this.mc.player.getViewYRot(pt));
      double tip = this.followHead.get() ? Mth.clamp(Wearer.pitchRad * 0.35, -0.35, 0.35) : 0.0;
      double fX = -Math.sin(headYaw), fZ = Math.cos(headYaw);
      double rX = -fZ, rZ = fX; // right of the head
      // basis: A = right, B = forward tipped, N = up tipped
      double ct = Math.cos(tip), st = Math.sin(tip);
      double bX = fX * ct, bY = -st, bZ = fZ * ct;
      double nX = fX * st, nY = ct, nZ = fZ * st;
      double aX = rX, aY = 0.0, aZ = rZ;
      if (this.followHead.get()) {
         // keep the ring over the crown when the head tips: shift with it
         cx += fX * tip * 0.25;
         cz += fZ * tip * 0.25;
      }
      double r = this.radius.get();
      double tube = this.thickness.get();
      double spin = time * this.spinSpeed.get() * Math.PI * 2.0;
      float light = 1.0F;
      Design d = this.design.get();
      switch (d) {
         case Classic -> this.torus(event.solidRenderer, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r, tube, spin, time, 0, 1, light);
         case Double -> {
            this.torus(event.solidRenderer, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r, tube * 0.75, spin, time, 0, 1, light);
            this.torus(event.solidRenderer, cx + nX * 0.06, cy + nY * 0.06, cz + nZ * 0.06, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r * 0.74, tube * 0.6, -spin * 1.4, time, 0, 1, light);
         }
         case Radiant -> {
            this.torus(event.solidRenderer, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r, tube, spin, time, 0, 1, light);
            this.rays(event, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r, tube, spin, time);
         }
         case Segmented -> this.torus(event.solidRenderer, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, r, tube, spin, time, 8, 2, light);
      }

      float g = this.glow.get().floatValue();
      if (g > 0.0F) {
         double w = tube * 3.5 + 0.05 * g;
         int alpha = (int)Math.min(255, 130 * g);
         this.glowBand(event.glowRenderer, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, r, w, spin, time, alpha);
         if (d == Design.Double) this.glowBand(event.glowRenderer, cx + nX * 0.06, cy + nY * 0.06, cz + nZ * 0.06, aX, aY, aZ, bX, bY, bZ, r * 0.74, w * 0.7, -spin, time, alpha);
      }
   }

   /**
    * A torus around N. {@code gaps} > 0 cuts it into that many arcs (every {@code keepEvery}-th piece kept). Shaded
    * brighter on top so it reads as a solid ring even though it is self-lit.
    */
   private void torus(
      Renderer3D r, double cx, double cy, double cz, double aX, double aY, double aZ, double bX, double bY, double bZ, double nX, double nY, double nZ,
      double radius, double tube, double spin, double time, int gaps, int keepEvery, float light
   ) {
      MeshBuilder m = r.triangles;
      int arcLen = gaps > 0 ? SEGMENTS / gaps : SEGMENTS;
      for (int i = 0; i < SEGMENTS; i++) {
         if (gaps > 0 && (i / arcLen) % keepEvery != 0) continue;
         if (gaps > 0 && i % arcLen == arcLen - 1) continue;
         double t0 = spin + Math.PI * 2.0 * i / SEGMENTS, t1 = spin + Math.PI * 2.0 * (i + 1) / SEGMENTS;
         int c0 = this.colorAt((double)i / SEGMENTS, time), c1 = this.colorAt((double)(i + 1) / SEGMENTS, time);
         m.ensureCapacity(SIDES * 4, SIDES * 6);
         for (int k = 0; k < SIDES; k++) {
            double p0 = Math.PI * 2.0 * k / SIDES, p1 = Math.PI * 2.0 * (k + 1) / SIDES;
            float s0 = (float)(0.72 + 0.28 * Math.sin(p0)), s1 = (float)(0.72 + 0.28 * Math.sin(p1));
            int v00 = this.vert(m, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, radius, tube, t0, p0, Fx.shade(c0, s0 * light));
            int v01 = this.vert(m, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, radius, tube, t0, p1, Fx.shade(c0, s1 * light));
            int v11 = this.vert(m, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, radius, tube, t1, p1, Fx.shade(c1, s1 * light));
            int v10 = this.vert(m, cx, cy, cz, aX, aY, aZ, bX, bY, bZ, nX, nY, nZ, radius, tube, t1, p0, Fx.shade(c1, s0 * light));
            m.quad(v00, v01, v11, v10);
         }
      }
   }

   private int vert(
      MeshBuilder m, double cx, double cy, double cz, double aX, double aY, double aZ, double bX, double bY, double bZ, double nX, double nY, double nZ,
      double radius, double tube, double theta, double phi, int color
   ) {
      double ring = radius + tube * Math.cos(phi);
      double ct = Math.cos(theta) * ring, st = Math.sin(theta) * ring, up = tube * Math.sin(phi);
      return m.vec3(cx + aX * ct + bX * st + nX * up, cy + aY * ct + bY * st + nY * up, cz + aZ * ct + bZ * st + nZ * up).color(color).next();
   }

   /** Soft light on both sides of the ring, in its plane. */
   private void glowBand(
      Renderer3D r, double cx, double cy, double cz, double aX, double aY, double aZ, double bX, double bY, double bZ, double radius, double width, double spin,
      double time, int alpha
   ) {
      MeshBuilder m = r.triangles;
      int prevIn = -1, prevMid = -1, prevOut = -1;
      m.ensureCapacity((SEGMENTS + 1) * 3, SEGMENTS * 12);
      for (int i = 0; i <= SEGMENTS; i++) {
         double t = spin + Math.PI * 2.0 * i / SEGMENTS;
         double c = Math.cos(t), s = Math.sin(t);
         int col = this.colorAt((double)i / SEGMENTS, time) & 0xFFFFFF;
         double ri = Math.max(0.0, radius - width), ro = radius + width;
         int vIn = m.vec3(cx + (aX * c + bX * s) * ri, cy + (aY * c + bY * s) * ri, cz + (aZ * c + bZ * s) * ri).color(col).next();
         int vMid = m.vec3(cx + (aX * c + bX * s) * radius, cy + (aY * c + bY * s) * radius, cz + (aZ * c + bZ * s) * radius).color(alpha << 24 | col).next();
         int vOut = m.vec3(cx + (aX * c + bX * s) * ro, cy + (aY * c + bY * s) * ro, cz + (aZ * c + bZ * s) * ro).color(col).next();
         if (prevIn >= 0) {
            m.quad(prevIn, prevMid, vMid, vIn);
            m.quad(prevMid, prevOut, vOut, vMid);
         }
         prevIn = vIn;
         prevMid = vMid;
         prevOut = vOut;
      }
   }

   /** Twelve thin rays pointing out of the ring, lit additively. */
   private void rays(
      Render3DEvent e, double cx, double cy, double cz, double aX, double aY, double aZ, double bX, double bY, double bZ, double nX, double nY, double nZ,
      double radius, double tube, double spin, double time
   ) {
      int n = 12;
      for (int i = 0; i < n; i++) {
         double t = -spin * 0.5 + Math.PI * 2.0 * i / n;
         double c = Math.cos(t), s = Math.sin(t);
         double dx = aX * c + bX * s, dy = aY * c + bY * s, dz = aZ * c + bZ * s;
         double sx = -aX * s + bX * c, sy = -aY * s + bY * c, sz = -aZ * s + bZ * c;
         double len = (i % 2 == 0 ? 0.2 : 0.12) * (radius / 0.3) * (0.85 + 0.15 * Math.sin(time * 4.0 + i));
         double w = tube * 0.8;
         double bx = cx + dx * (radius + tube), by = cy + dy * (radius + tube), bz = cz + dz * (radius + tube);
         double tx = bx + dx * len + nX * len * 0.15, ty = by + dy * len + nY * len * 0.15, tz = bz + dz * len + nZ * len * 0.15;
         int col = this.colorAt((double)i / n, time);
         com.jfant.specter.cosmetic.Draw3D.tri(e.solidRenderer, bx + sx * w, by + sy * w, bz + sz * w, col, bx - sx * w, by - sy * w, bz - sz * w, col, tx, ty, tz, Fx.lerp(col, 0xFFFFFFFF, 0.5F));
         com.jfant.specter.cosmetic.Draw3D.tri(e.glowRenderer, bx + sx * w * 3, by + sy * w * 3, bz + sz * w * 3, Fx.alpha(col, 110), bx - sx * w * 3, by - sy * w * 3, bz - sz * w * 3,
            Fx.alpha(col, 110), tx + dx * 0.05, ty + dy * 0.05, tz + dz * 0.05, col & 0xFFFFFF);
      }
   }
}
