package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.cosmetic.Draw3D;
import com.jfant.specter.cosmetic.Fx;
import com.jfant.specter.cosmetic.ParticleSystem;
import com.jfant.specter.cosmetic.Shapes;
import com.jfant.specter.cosmetic.SkinVoxels;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Death Animations: when a player dies, their body is replaced by a copy made of small cubes taken from their own skin
 * ({@link SkinVoxels}), and that copy is taken apart:
 *
 * <ul>
 *   <li>Ice Spike - ice spikes burst out of the ground around them, frost climbs the body, then everything shatters
 *       into ice shards and frozen cubes that bounce on the floor.</li>
 *   <li>Disintegrate - from the head down, a glowing line eats the body; everything above it crumbles into drifting
 *       ash and embers.</li>
 *   <li>Water Dissolve - the body turns to water from the top, drops fall and gather in a puddle that spreads and
 *       soaks away.</li>
 * </ul>
 *
 * Purely cosmetic and client-side: no blocks are placed or changed, nothing is sent to the server. The vanilla falling
 * body is hidden while an animation plays (see {@code CosmeticLivingRendererMixin}).
 */
public class DeathAnimations extends Module {
   public enum Preset {
      IceSpike,
      Disintegrate,
      WaterDissolve,
      Random
   }

   public enum Who {
      Self,
      OtherPlayers,
      Everyone
   }

   public enum Density {
      Low,
      High
   }

   private static final int MAX_ACTIVE = 4;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colours");
   private final Setting<Preset> preset = this.sgGeneral
      .add(new EnumSetting.Builder<Preset>().name("animation").description("Which death animation plays.").defaultValue(Preset.IceSpike).build());
   private final Setting<Who> who = this.sgGeneral
      .add(new EnumSetting.Builder<Who>().name("players").description("Whose deaths get the animation.").defaultValue(Who.Everyone).build());
   private final Setting<Double> duration = this.sgGeneral
      .add(new DoubleSetting.Builder().name("duration").description("Seconds the animation lasts.").defaultValue(2.5).range(0.8, 6.0).sliderRange(0.8, 6.0).build());
   private final Setting<Density> density = this.sgGeneral
      .add(
         new EnumSetting.Builder<Density>()
            .name("particle-density")
            .description("High: one cube per skin pixel (about a thousand). Low: one per 2x2x2 pixels, much cheaper.")
            .defaultValue(Density.High)
            .build()
      );
   private final Setting<Double> glow = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow").description("Strength of the glows (frost, embers, shimmer).").defaultValue(1.0).range(0.0, 2.0).sliderRange(0.0, 2.0).build());
   private final Setting<SettingColor> iceColor = this.sgColors
      .add(new ColorSetting.Builder().name("ice-colour").description("Ice Spike colour.").defaultValue(new SettingColor(150, 215, 255)).build());
   private final Setting<SettingColor> emberColor = this.sgColors
      .add(new ColorSetting.Builder().name("ember-colour").description("Disintegrate glow colour.").defaultValue(new SettingColor(170, 90, 255)).build());
   private final Setting<SettingColor> waterColor = this.sgColors
      .add(new ColorSetting.Builder().name("water-colour").description("Water Dissolve colour.").defaultValue(new SettingColor(40, 120, 230)).build());

   private final ParticleSystem fx = new ParticleSystem(9000);
   private final List<Death> active = new ArrayList<>();
   /** Players whose death already started an animation (cleared when they are alive again). */
   private final IntOpenHashSet started = new IntOpenHashSet();
   private final Int2ObjectOpenHashMap<Death> byId = new Int2ObjectOpenHashMap<>();
   private final RandomSource random = RandomSource.create();
   private int ticks;

   /** One playing animation. */
   private static final class Death {
      int entityId;
      Preset preset;
      SkinVoxels voxels;
      double x, y, z, yaw;
      int startTick, lengthTicks;
      boolean[] released;
      float[] releaseAt;
      double[] spikeX, spikeZ, spikeH, spikeTilt;
      boolean shattered;
      float puddle;
   }

   public DeathAnimations() {
      super(SpecterClientAddon.VISUALS, "death-animations", "Players who die turn into ice and shatter, crumble from the head down, or dissolve into water. Cosmetic, only you see it.");
   }

   /** Whether the vanilla body of entity {@code id} is hidden because an animation replaces it. */
   public static boolean hides(int id) {
      DeathAnimations m = Modules.get().get(DeathAnimations.class);
      return m != null && m.isActive() && (m.byId.containsKey(id) || m.started.contains(id));
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   private void reset() {
      this.active.clear();
      this.byId.clear();
      this.started.clear();
      this.fx.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   private boolean wanted(AbstractClientPlayer p) {
      boolean self = p == this.mc.player;
      return switch (this.who.get()) {
         case Self -> self;
         case OtherPlayers -> !self;
         case Everyone -> true;
      };
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level == null) return;
      this.ticks++;
      for (AbstractClientPlayer p : this.mc.level.players()) {
         int id = p.getId();
         if (!p.isDeadOrDying()) {
            this.started.remove(id);
            continue;
         }
         if (this.started.contains(id) || !this.wanted(p) || this.active.size() >= MAX_ACTIVE) continue;
         this.started.add(id);
         this.start(p);
      }

      for (Iterator<Death> it = this.active.iterator(); it.hasNext(); ) {
         Death d = it.next();
         float t = (float)(this.ticks - d.startTick) / d.lengthTicks;
         this.step(d, t);
         if (t >= 1.0F) {
            it.remove();
            this.byId.remove(d.entityId);
         }
      }
      this.fx.tick(this.mc.level);
   }

   private void start(AbstractClientPlayer p) {
      Death d = new Death();
      d.entityId = p.getId();
      d.preset = this.preset.get() == Preset.Random ? Preset.values()[this.random.nextInt(3)] : this.preset.get();
      d.voxels = SkinVoxels.of(p, this.density.get() == Density.High ? 1 : 2);
      d.x = p.getX();
      d.y = p.getY();
      d.z = p.getZ();
      d.yaw = Math.toRadians(p.yBodyRot);
      d.startTick = this.ticks;
      d.lengthTicks = Math.max(16, (int)Math.round(this.duration.get() * 20.0));
      d.released = new boolean[d.voxels.count];
      d.releaseAt = new float[d.voxels.count];
      float top = 32.0F * SkinVoxels.PIXEL;
      for (int i = 0; i < d.voxels.count; i++) {
         float height = d.voxels.y[i] / top; // 0 feet .. 1 crown
         d.releaseAt[i] = switch (d.preset) {
            case Disintegrate -> 0.05F + (1.0F - height) * 0.75F + this.random.nextFloat() * 0.04F;
            case WaterDissolve -> 0.08F + (1.0F - height) * 0.55F + this.random.nextFloat() * 0.12F;
            default -> 0.45F;
         };
      }
      if (d.preset == Preset.IceSpike) {
         int n = 9;
         d.spikeX = new double[n];
         d.spikeZ = new double[n];
         d.spikeH = new double[n];
         d.spikeTilt = new double[n];
         for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n + this.random.nextDouble() * 0.4;
            double r = 0.45 + this.random.nextDouble() * 0.35;
            d.spikeX[i] = Math.cos(a) * r;
            d.spikeZ[i] = Math.sin(a) * r;
            d.spikeH[i] = 1.3 + this.random.nextDouble() * 1.1;
            d.spikeTilt[i] = 0.25 + this.random.nextDouble() * 0.25;
         }
      }
      this.active.add(d);
      this.byId.put(d.entityId, d);
   }

   // ============================================================================================== simulation

   /** World position of voxel i (before any animation offset). */
   private static double wx(Death d, int i) {
      double c = Math.cos(d.yaw), s = Math.sin(d.yaw);
      // model x is the player's left; at yaw 0 the player faces +z and their left is +x
      return d.x + d.voxels.x[i] * c - d.voxels.z[i] * s;
   }

   private static double wz(Death d, int i) {
      double c = Math.cos(d.yaw), s = Math.sin(d.yaw);
      return d.z + d.voxels.x[i] * s + d.voxels.z[i] * c;
   }

   private void step(Death d, float t) {
      switch (d.preset) {
         case IceSpike -> {
            if (!d.shattered && t >= 0.45F) {
               d.shattered = true;
               this.shatter(d);
            } else if (!d.shattered && this.ticks % 2 == 0) {
               this.frost(d);
            }
         }
         case Disintegrate -> this.releaseVoxels(d, t, true);
         case WaterDissolve -> {
            this.releaseVoxels(d, t, false);
            int released = 0;
            for (boolean b : d.released) if (b) released++;
            d.puddle = (float)released / Math.max(1, d.voxels.count);
         }
         default -> {
         }
      }
   }

   private void frost(Death d) {
      ParticleSystem.Spec s = this.fx.spec.reset();
      s.shape = Shapes.ID_SPARK;
      s.flags = ParticleSystem.BILLBOARD | ParticleSystem.ADDITIVE | ParticleSystem.SHRINK;
      s.life = 12 + this.random.nextInt(10);
      s.size0 = 0.08F;
      s.color0 = Fx.alpha(this.iceColor.get().getPacked(), 230);
      s.spinV = 0.2F;
      double a = this.random.nextDouble() * Math.PI * 2.0;
      this.fx.spawn(d.x + Math.cos(a) * 0.4, d.y + this.random.nextDouble() * 2.0, d.z + Math.sin(a) * 0.4, 0.0, 0.01, 0.0);
   }

   private void shatter(Death d) {
      int ice = this.iceColor.get().getPacked() | 0xFF000000;
      ParticleSystem.Spec s = this.fx.spec;
      int left = d.lengthTicks - (this.ticks - d.startTick);
      // the spikes break into shards
      for (int k = 0; k < d.spikeX.length; k++) {
         for (int j = 0; j < 10; j++) {
            double h = this.random.nextDouble() * d.spikeH[k];
            s.reset();
            s.shape = Shapes.ID_SHARD;
            s.flags = ParticleSystem.FLOOR | ParticleSystem.GLOW;
            s.life = left + this.random.nextInt(10);
            s.size0 = s.size1 = 0.12F + this.random.nextFloat() * 0.12F;
            s.color0 = Fx.alpha(Fx.lerp(ice, 0xFFFFFFFF, this.random.nextFloat() * 0.5F), 210);
            s.color1 = ice;
            s.glow = Fx.alpha(ice, (int)(80 * this.glow.get()));
            s.gravity = 0.04F;
            s.drag = 0.97F;
            s.bounce = 0.35F;
            s.yaw = this.random.nextFloat() * 6.28F;
            s.yawV = (this.random.nextFloat() - 0.5F) * 0.6F;
            s.tiltV = (this.random.nextFloat() - 0.5F) * 0.6F;
            s.spinV = (this.random.nextFloat() - 0.5F) * 0.5F;
            double ox = d.spikeX[k], oz = d.spikeZ[k];
            this.fx.spawn(d.x + ox, d.y + h, d.z + oz, ox * 0.25 + (this.random.nextFloat() - 0.5F) * 0.15, 0.15 + this.random.nextFloat() * 0.25,
               oz * 0.25 + (this.random.nextFloat() - 0.5F) * 0.15);
         }
      }
      // the frozen body bursts into cubes
      for (int i = 0; i < d.voxels.count; i++) {
         d.released[i] = true;
         s.reset();
         s.shape = ParticleSystem.SHAPE_CUBE;
         s.flags = ParticleSystem.FLOOR | ParticleSystem.SOLID;
         s.life = left + this.random.nextInt(12);
         s.size0 = s.size1 = d.voxels.size;
         s.color0 = Fx.lerp(d.voxels.color[i], ice, 0.55F);
         s.color1 = Fx.lerp(d.voxels.color[i], 0xFFFFFFFF, 0.6F);
         s.gravity = 0.045F;
         s.drag = 0.96F;
         s.bounce = 0.3F;
         double px = wx(d, i), py = d.y + d.voxels.y[i], pz = wz(d, i);
         double ox = px - d.x, oz = pz - d.z;
         this.fx.spawn(px, py, pz, ox * 0.5 + (this.random.nextFloat() - 0.5F) * 0.12, 0.08 + this.random.nextFloat() * 0.22, oz * 0.5 + (this.random.nextFloat() - 0.5F) * 0.12);
      }
   }

   private void releaseVoxels(Death d, float t, boolean ash) {
      ParticleSystem.Spec s = this.fx.spec;
      int left = Math.max(8, d.lengthTicks - (this.ticks - d.startTick));
      int ember = this.emberColor.get().getPacked() | 0xFF000000;
      int water = this.waterColor.get().getPacked() | 0xFF000000;
      for (int i = 0; i < d.voxels.count; i++) {
         if (d.released[i] || t < d.releaseAt[i]) continue;
         d.released[i] = true;
         double px = wx(d, i), py = d.y + d.voxels.y[i], pz = wz(d, i);
         s.reset();
         if (ash) {
            s.shape = ParticleSystem.SHAPE_CUBE;
            s.flags = ParticleSystem.SOLID | ParticleSystem.SHRINK;
            s.life = Math.min(left + 10, 18 + this.random.nextInt(20));
            s.size0 = d.voxels.size;
            s.color0 = d.voxels.color[i];
            s.color1 = 0xFF3A3640;
            s.gravity = -0.004F;
            s.drag = 0.9F;
            this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.06, 0.02 + this.random.nextFloat() * 0.03, (this.random.nextFloat() - 0.5F) * 0.06);
            if (this.random.nextInt(4) == 0) {
               s.reset();
               s.shape = Shapes.ID_SPARK;
               s.flags = ParticleSystem.BILLBOARD | ParticleSystem.ADDITIVE | ParticleSystem.SHRINK;
               s.life = 14 + this.random.nextInt(12);
               s.size0 = 0.09F;
               s.color0 = Fx.alpha(ember, (int)Math.min(255, 220 * this.glow.get()));
               s.gravity = -0.003F;
               s.spinV = 0.3F;
               this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.05, 0.03, (this.random.nextFloat() - 0.5F) * 0.05);
            }
         } else {
            s.shape = Shapes.ID_DROPLET;
            s.flags = ParticleSystem.BILLBOARD | ParticleSystem.FLOOR | ParticleSystem.SETTLE | ParticleSystem.GLOW;
            s.life = left + 4;
            s.size0 = s.size1 = d.voxels.size * 1.5F;
            s.color0 = Fx.alpha(Fx.lerp(water, 0xFFFFFFFF, 0.25F), 200);
            s.color1 = Fx.alpha(water, 160);
            s.glow = Fx.alpha(water, (int)(40 * this.glow.get()));
            s.gravity = 0.03F;
            s.drag = 0.98F;
            this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.03, -0.02, (this.random.nextFloat() - 0.5F) * 0.03);
         }
      }
   }

   // ============================================================================================== drawing

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level == null || (this.active.isEmpty() && this.fx.count() == 0)) return;
      Fx.camera();
      float pt = event.tickDelta;
      for (Death d : this.active) {
         float t = Mth.clamp((this.ticks - d.startTick + pt) / d.lengthTicks, 0.0F, 1.0F);
         switch (d.preset) {
            case IceSpike -> this.drawIce(event, d, t);
            case Disintegrate -> this.drawDisintegrate(event, d, t);
            case WaterDissolve -> this.drawWater(event, d, t);
            default -> {
            }
         }
      }
      this.fx.render(event.solidRenderer, event.depthRenderer, event.glowRenderer, pt, this.glow.get().floatValue());
   }

   private void drawBody(Render3DEvent e, Death d, int tintTo, float tint, float frostLine, float sag, float shrink) {
      float light = Fx.light(this.mc.level, d.x, d.y + 1.0, d.z);
      SkinVoxels v = d.voxels;
      double h = v.size * 0.5 * shrink;
      for (int i = 0; i < v.count; i++) {
         if (d.released[i]) continue;
         double px = wx(d, i), py = d.y + v.y[i] * (1.0 - sag), pz = wz(d, i);
         float local = frostLine < 0.0F ? tint : (v.y[i] < frostLine ? tint : 0.0F);
         int c = Fx.shade(local > 0.0F ? Fx.lerp(v.color[i], tintTo, local) : v.color[i], light);
         Draw3D.boxFacing(e.solidRenderer, px - h, py - h, pz - h, px + h, py + h, pz + h, c, Fx.BLOCK_SHADE, Fx.camX, Fx.camY, Fx.camZ);
      }
   }

   private void drawIce(Render3DEvent e, Death d, float t) {
      int ice = this.iceColor.get().getPacked() | 0xFF000000;
      float g = this.glow.get().floatValue();
      if (!d.shattered) {
         float grow = Fx.easeOutBack(Mth.clamp(t / 0.28F, 0.0F, 1.0F));
         float frost = Mth.clamp(t / 0.35F, 0.0F, 1.0F) * 34.0F * SkinVoxels.PIXEL;
         this.drawBody(e, d, Fx.lerp(ice, 0xFFFFFFFF, 0.3F), 0.65F, frost, 0.0F, 1.0F);
         for (int k = 0; k < d.spikeX.length; k++) this.spike(e, d, k, grow, ice, g);
         if (g > 0.0F) {
            double r = 1.4;
            Draw3D.glow(e.glowRenderer, d.x, d.y + 1.0, d.z, Fx.rightX * r, Fx.rightY * r, Fx.rightZ * r, Fx.upX * r, Fx.upY * r, Fx.upZ * r,
               Fx.alpha(ice, (int)(55 * g * grow)));
         }
      }
   }

   /** One ice spike: a four-sided crystal leaning in over the body, frosted at the base, bright along the edges. */
   private void spike(Render3DEvent e, Death d, int k, float grow, int ice, float g) {
      double bx = d.x + d.spikeX[k], bz = d.z + d.spikeZ[k], by = d.y - 0.1;
      double height = d.spikeH[k] * grow;
      if (height <= 0.02) return;
      double lean = d.spikeTilt[k];
      double ax = bx - d.spikeX[k] * lean, ay = by + height, az = bz - d.spikeZ[k] * lean;
      double w = 0.16 + d.spikeH[k] * 0.06;
      int body = Fx.alpha(Fx.lerp(ice, 0xFFFFFFFF, 0.35F), 170);
      int tip = Fx.alpha(0xFFFFFFFF, 220);
      double[][] base = {{bx - w, bz - w}, {bx + w, bz - w}, {bx + w, bz + w}, {bx - w, bz + w}};
      for (int f = 0; f < 4; f++) {
         double[] p0 = base[f], p1 = base[(f + 1) % 4];
         int shade = Fx.shade(body, f % 2 == 0 ? 1.0F : 0.82F);
         Draw3D.tri(e.depthRenderer, p0[0], by, p0[1], shade, p1[0], by, p1[1], shade, ax, ay, az, tip);
         if (g > 0.0F) Draw3D.tri(e.glowRenderer, p0[0], by, p0[1], Fx.alpha(ice, (int)(60 * g)), p1[0], by, p1[1], Fx.alpha(ice, (int)(60 * g)), ax, ay, az, Fx.alpha(0xFFFFFFFF, (int)(40 * g)));
      }
   }

   private void drawDisintegrate(Render3DEvent e, Death d, float t) {
      this.drawBody(e, d, 0xFF2A2630, 0.0F, -1.0F, 0.0F, 1.0F);
      // the glowing line where the body is being eaten
      float top = 32.0F * SkinVoxels.PIXEL;
      float line = Mth.clamp(1.0F - (t - 0.05F) / 0.75F, 0.0F, 1.0F) * top;
      float g = this.glow.get().floatValue();
      if (line > 0.0F && t < 0.82F && g > 0.0F) {
         int ember = this.emberColor.get().getPacked();
         Draw3D.ring(e.glowRenderer, d.x, d.y + line, d.z, 0.0, 0.42, 24, Fx.alpha(ember, (int)Math.min(255, 150 * g)), ember & 0xFFFFFF, 0.0);
         Draw3D.ring(e.glowRenderer, d.x, d.y + line + 0.02, d.z, 0.3, 0.7, 24, Fx.alpha(ember, (int)Math.min(255, 70 * g)), ember & 0xFFFFFF, 0.0);
      }
   }

   private void drawWater(Render3DEvent e, Death d, float t) {
      int water = this.waterColor.get().getPacked() | 0xFF000000;
      // the body turns to water from the top and slumps a little as it melts
      float tint = Mth.clamp(t / 0.35F, 0.0F, 1.0F);
      this.drawBody(e, d, Fx.lerp(water, 0xFFFFFFFF, 0.2F), tint * 0.8F, -1.0F, tint * 0.12F, 1.0F);
      // the puddle grows with what has melted, then soaks away
      float soak = t > 0.7F ? 1.0F - (t - 0.7F) / 0.3F : 1.0F;
      float r = 0.25F + d.puddle * 1.1F;
      if (d.puddle > 0.0F && soak > 0.0F) {
         int centre = Fx.alpha(Fx.lerp(water, 0xFFFFFFFF, 0.15F), (int)(170 * soak));
         double y = Math.floor(d.y + 0.01) + 0.02;
         Draw3D.ring(e.depthRenderer, d.x, y, d.z, 0.0, r, 32, centre, Fx.alpha(water, (int)(90 * soak)), 0.0);
         float g = this.glow.get().floatValue();
         if (g > 0.0F) Draw3D.ring(e.glowRenderer, d.x, y + 0.01, d.z, r * 0.6, r, 32, water & 0xFFFFFF, Fx.alpha(0xFFFFFFFF, (int)(35 * g * soak)), 0.0);
      }
   }
}
