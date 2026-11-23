package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.cosmetic.Draw3D;
import com.jfant.specter.cosmetic.Fx;
import com.jfant.specter.cosmetic.ParticleSystem;
import com.jfant.specter.cosmetic.Shapes;
import com.jfant.specter.cosmetic.Wearer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Relics: small glowing objects that float around you.
 *
 * <ul>
 *   <li>Playing Cards - real cards (rounded, white face, red/black pips, both sides) with a red outer glow.</li>
 *   <li>Sakura - pink cherry petals with a soft pink glow that also shed little petals that drift down and settle.</li>
 *   <li>Game Chips - thick casino chips (striped rim, inner ring) with a green outer glow.</li>
 * </ul>
 *
 * Each relic is pulled towards a moving spot by a damped spring and pushed by a little wander noise, so they drift and
 * lag naturally instead of being glued to a circle. Bodies are drawn opaque (they hide each other and hide behind
 * blocks correctly), glows additively. Purely client-side: only you see them.
 */
public class Relics extends Module {
   public enum Style {
      PlayingCards,
      Sakura,
      GameChips
   }

   public enum Preset {
      Orbit,
      Swirl,
      Drift,
      Crown
   }

   private static final int MAX = 24;
   private static final int[] CHIP_COLORS = new int[]{0xFF1E8E3E, 0xFFC62828, 0xFF1F1F1F, 0xFF1565C0, 0xFF6A1B9A, 0xFFEF8F00};

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colours");
   private final Setting<Style> style = this.sgGeneral
      .add(new EnumSetting.Builder<Style>().name("style").description("What floats around you.").defaultValue(Style.PlayingCards).build());
   private final Setting<Preset> preset = this.sgGeneral
      .add(
         new EnumSetting.Builder<Preset>()
            .name("animation")
            .description("Orbit: a steady ring at chest height. Swirl: a rising spiral around you. Drift: a loose cloud that lags behind. Crown: a ring over your head.")
            .defaultValue(Preset.Orbit)
            .build()
      );
   private final Setting<Integer> count = this.sgGeneral
      .add(new IntSetting.Builder().name("count").description("How many relics.").defaultValue(6).range(1, MAX).sliderRange(1, MAX).build());
   private final Setting<Double> size = this.sgGeneral
      .add(new DoubleSetting.Builder().name("size").description("Relic size.").defaultValue(1.0).range(0.4, 2.5).sliderRange(0.4, 2.0).build());
   private final Setting<Double> speed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("speed").description("How fast they move around you.").defaultValue(1.0).range(0.1, 3.0).sliderRange(0.1, 3.0).build());
   private final Setting<Double> spread = this.sgGeneral
      .add(new DoubleSetting.Builder().name("spread").description("How far from you they float, in blocks.").defaultValue(1.25).range(0.6, 4.0).sliderRange(0.6, 3.0).build());
   private final Setting<Double> glow = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow").description("Outer glow strength. 0 turns the glow off.").defaultValue(1.0).range(0.0, 2.5).sliderRange(0.0, 2.0).build());
   private final Setting<Integer> particles = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("particles")
            .description("Little sparks/petals the relics shed, per relic per second. 0 for none.")
            .defaultValue(2)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Boolean> firstPerson = this.sgGeneral
      .add(new BoolSetting.Builder().name("first-person").description("Also show them in first person (ones right in front of the camera are skipped).").defaultValue(true).build());
   private final Setting<Boolean> customColors = this.sgColors
      .add(new BoolSetting.Builder().name("custom-colours").description("Use your own colours instead of the style's.").defaultValue(false).build());
   private final Setting<SettingColor> mainColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("main-colour")
            .description("Card pips / petal / chip colour.")
            .defaultValue(new SettingColor(214, 22, 48))
            .visible(this.customColors::get)
            .build()
      );
   private final Setting<SettingColor> glowColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("glow-colour")
            .description("Outer glow colour.")
            .defaultValue(new SettingColor(255, 40, 60))
            .visible(this.customColors::get)
            .build()
      );

   private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
   private final float[] ox = new float[MAX], oy = new float[MAX], oz = new float[MAX];
   private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
   private final float[] spin = new float[MAX], ospin = new float[MAX], tilt = new float[MAX], otilt = new float[MAX], phase = new float[MAX];
   private final int[] variant = new int[MAX];
   private double baseX, baseY, baseZ;
   private boolean placed;
   private int ticks;
   private final RandomSource random = RandomSource.create();
   private final ParticleSystem fx = new ParticleSystem(600);

   public Relics() {
      super(SpecterClientAddon.VISUALS, "relics", "Playing cards, sakura petals or casino chips float around you with a soft glow. Only you see them.");
   }

   @Override
   public void onActivate() {
      this.placed = false;
      this.fx.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.placed = false;
      this.fx.clear();
   }

   private int glowRgb() {
      if (this.customColors.get()) return this.glowColor.get().getPacked() & 0xFFFFFF;
      return switch (this.style.get()) {
         case PlayingCards -> 0xFF2238;
         case Sakura -> 0xFF8CC6;
         case GameChips -> 0x2BFF6A;
      };
   }

   private int mainRgb() {
      if (this.customColors.get()) return this.mainColor.get().getPacked() & 0xFFFFFF;
      return switch (this.style.get()) {
         case PlayingCards -> 0xD0142C;
         case Sakura -> 0xF58FB5;
         case GameChips -> 0x1E8E3E;
      };
   }

   /** Where relic {@code i} of {@code n} wants to be, relative to the feet. */
   private void target(int i, int n, double t, double[] out) {
      double spreadR = this.spread.get();
      double a = Math.PI * 2.0 * i / n + t;
      switch (this.preset.get()) {
         case Orbit -> {
            out[0] = Math.cos(a) * spreadR;
            out[1] = 1.15 + Math.sin(t * 1.7 + this.phase[i] * 6.0) * 0.12;
            out[2] = Math.sin(a) * spreadR;
         }
         case Swirl -> {
            double h = (t * 0.25 + (double)i / n) % 1.0;
            double r = spreadR * (0.8 + 0.25 * Math.sin(h * Math.PI));
            double sa = a * 1.6;
            out[0] = Math.cos(sa) * r;
            out[1] = 0.1 + h * 2.2;
            out[2] = Math.sin(sa) * r;
         }
         case Drift -> {
            double r = spreadR * (0.7 + 0.35 * Math.sin(this.phase[i] * 11.0 + t * 0.6));
            double da = Math.PI * 2.0 * this.phase[i] + t * 0.35;
            out[0] = Math.cos(da) * r;
            out[1] = 0.9 + this.phase[i] * 1.1 + Math.sin(t * 0.9 + i) * 0.2;
            out[2] = Math.sin(da) * r;
         }
         case Crown -> {
            double r = 0.45 + spreadR * 0.25;
            out[0] = Math.cos(a) * r;
            out[1] = 2.25 + Math.sin(t * 2.0 + i) * 0.05;
            out[2] = Math.sin(a) * r;
         }
      }
   }

   private final double[] target = new double[3];

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      this.ticks++;
      int n = this.count.get();
      double fx0 = this.mc.player.getX(), fy0 = this.mc.player.getY(), fz0 = this.mc.player.getZ();
      if (!this.placed || Math.abs(fx0 - this.baseX) > 64.0 || Math.abs(fz0 - this.baseZ) > 64.0 || Math.abs(fy0 - this.baseY) > 64.0) {
         this.baseX = fx0;
         this.baseY = fy0;
         this.baseZ = fz0;
         for (int i = 0; i < MAX; i++) {
            this.phase[i] = this.random.nextFloat();
            this.variant[i] = this.random.nextInt(4);
            this.px[i] = this.ox[i] = (float)(this.random.nextGaussian() * 0.3);
            this.py[i] = this.oy[i] = 1.0F;
            this.pz[i] = this.oz[i] = (float)(this.random.nextGaussian() * 0.3);
            this.vx[i] = this.vy[i] = this.vz[i] = 0.0F;
            this.spin[i] = this.ospin[i] = this.random.nextFloat() * 6.28F;
            this.tilt[i] = this.otilt[i] = 0.0F;
         }
         this.placed = true;
      }

      double t = this.ticks / 20.0 * this.speed.get();
      boolean loose = this.preset.get() == Preset.Drift;
      float stiff = loose ? 0.035F : 0.09F;
      float damp = loose ? 0.86F : 0.8F;
      float rx = (float)(fx0 - this.baseX), ry = (float)(fy0 - this.baseY), rz = (float)(fz0 - this.baseZ);
      for (int i = 0; i < n; i++) {
         this.ox[i] = this.px[i];
         this.oy[i] = this.py[i];
         this.oz[i] = this.pz[i];
         this.ospin[i] = this.spin[i];
         this.otilt[i] = this.tilt[i];
         this.target(i, n, t, this.target);
         float ax = (float)(rx + this.target[0] - this.px[i]) * stiff;
         float ay = (float)(ry + this.target[1] - this.py[i]) * stiff;
         float az = (float)(rz + this.target[2] - this.pz[i]) * stiff;
         // wander: a slow per-relic sway so they never move in lockstep
         ax += (float)Math.sin(t * 1.3 + this.phase[i] * 17.0) * 0.004F;
         ay += (float)Math.cos(t * 1.1 + this.phase[i] * 23.0) * 0.003F;
         az += (float)Math.sin(t * 0.9 + this.phase[i] * 29.0) * 0.004F;
         this.vx[i] = (this.vx[i] + ax) * damp;
         this.vy[i] = (this.vy[i] + ay) * damp;
         this.vz[i] = (this.vz[i] + az) * damp;
         float max = 0.8F;
         float sp = Mth.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i] + this.vz[i] * this.vz[i]);
         if (sp > max) {
            this.vx[i] *= max / sp;
            this.vy[i] *= max / sp;
            this.vz[i] *= max / sp;
         }
         this.px[i] += this.vx[i];
         this.py[i] += this.vy[i];
         this.pz[i] += this.vz[i];
         float turn = this.style.get() == Style.GameChips ? 0.16F : 0.07F;
         this.spin[i] += (float)((turn + this.phase[i] * 0.05) * this.speed.get());
         this.tilt[i] = (float)Math.sin(t * 1.4 + this.phase[i] * 9.0) * (this.style.get() == Style.Sakura ? 0.9F : 0.35F);
         this.shed(i);
      }
      this.fx.tick(this.mc.level);
   }

   /** Little sparks / petals left behind. */
   private void shed(int i) {
      int perSecond = this.particles.get();
      if (perSecond <= 0 || this.random.nextInt(20) >= perSecond) return;
      double wx = this.baseX + this.px[i], wy = this.baseY + this.py[i], wz = this.baseZ + this.pz[i];
      float s = this.size.get().floatValue();
      ParticleSystem.Spec spec = this.fx.spec.reset();
      int glowRgb = this.glowRgb();
      switch (this.style.get()) {
         case Sakura -> {
            spec.shape = Shapes.ID_PETAL;
            spec.flags = ParticleSystem.FLOOR | ParticleSystem.SETTLE | ParticleSystem.GLOW;
            spec.life = 60 + this.random.nextInt(60);
            spec.size0 = spec.size1 = 0.07F * s;
            spec.color0 = 0xFF000000 | this.mainRgb();
            spec.color1 = 0xFFFFE6EF;
            spec.glow = Fx.alpha(glowRgb, (int)(70 * this.glow.get()));
            spec.gravity = 0.0025F;
            spec.drag = 0.94F;
            spec.yaw = this.random.nextFloat() * 6.28F;
            spec.yawV = (this.random.nextFloat() - 0.5F) * 0.2F;
            spec.tiltV = (this.random.nextFloat() - 0.5F) * 0.25F;
            spec.spinV = (this.random.nextFloat() - 0.5F) * 0.2F;
            this.fx.spawn(wx, wy, wz, (this.random.nextFloat() - 0.5F) * 0.03F, -0.01F, (this.random.nextFloat() - 0.5F) * 0.03F);
         }
         default -> {
            spec.shape = Shapes.ID_SPARK;
            spec.flags = ParticleSystem.BILLBOARD | ParticleSystem.ADDITIVE | ParticleSystem.SHRINK;
            spec.life = 12 + this.random.nextInt(10);
            spec.size0 = 0.09F * s;
            spec.color0 = Fx.alpha(glowRgb, 230);
            spec.spinV = (this.random.nextFloat() - 0.5F) * 0.4F;
            spec.drag = 0.9F;
            this.fx.spawn(wx, wy, wz, (this.random.nextFloat() - 0.5F) * 0.04F, 0.01F + this.random.nextFloat() * 0.02F, (this.random.nextFloat() - 0.5F) * 0.04F);
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.placed || !Wearer.update(event.tickDelta)) return;
      if (!Wearer.thirdPerson && !this.firstPerson.get()) return;
      Fx.camera();
      float pt = event.tickDelta;
      int n = this.count.get();
      float s = this.size.get().floatValue();
      float glowAmount = this.glow.get().floatValue();
      int glowRgb = this.glowRgb();
      int main = this.mainRgb();
      for (int i = 0; i < n; i++) {
         double wx = this.baseX + Mth.lerp(pt, this.ox[i], this.px[i]);
         double wy = this.baseY + Mth.lerp(pt, this.oy[i], this.py[i]);
         double wz = this.baseZ + Mth.lerp(pt, this.oz[i], this.pz[i]);
         double cdx = wx - Fx.camX, cdy = wy - Fx.camY, cdz = wz - Fx.camZ;
         if (cdx * cdx + cdy * cdy + cdz * cdz < 0.36) continue;
         float sp = Mth.lerp(pt, this.ospin[i], this.spin[i]);
         float tl = Mth.lerp(pt, this.otilt[i], this.tilt[i]);
         // plane: turned by spin around Y, leaned by tilt
         double ca = Math.cos(sp), sa = Math.sin(sp), cb = Math.cos(tl), sb = Math.sin(tl);
         double rX = ca, rY = 0.0, rZ = -sa;
         double uX = sa * sb, uY = cb, uZ = ca * sb;
         float light = Math.max(0.72F, Fx.light(this.mc.level, wx, wy, wz));
         switch (this.style.get()) {
            case PlayingCards -> this.drawCard(event, i, wx, wy, wz, rX, rY, rZ, uX, uY, uZ, s * 0.42, main, glowRgb, glowAmount, light);
            case Sakura -> this.drawPetal(event, wx, wy, wz, rX, rY, rZ, uX, uY, uZ, s * 0.34, main, glowRgb, glowAmount, light);
            case GameChips -> this.drawChip(event, i, wx, wy, wz, sp, tl, s * 0.34, glowRgb, glowAmount, light);
         }
      }
      this.fx.render(event.solidRenderer, event.depthRenderer, event.glowRenderer, pt, glowAmount);
   }

   private static void soft(Renderer3D glow, double x, double y, double z, double radius, int rgb, float amount, int alpha) {
      if (amount <= 0.0F) return;
      double r = radius * (0.8 + amount * 0.5);
      Draw3D.glow(glow, x, y, z, Fx.rightX * r, Fx.rightY * r, Fx.rightZ * r, Fx.upX * r, Fx.upY * r, Fx.upZ * r, Fx.alpha(rgb, (int)Math.min(255, alpha * amount)));
   }

   private void drawCard(
      Render3DEvent e, int i, double x, double y, double z, double rX, double rY, double rZ, double uX, double uY, double uZ, double scale, int main, int glowRgb,
      float glowAmount, float light
   ) {
      double sx = scale, sy = scale;
      // normal = right x up, for lifting the print off the face
      double nX = rY * uZ - rZ * uY, nY = rZ * uX - rX * uZ, nZ = rX * uY - rY * uX;
      double lift = 0.004 * scale / 0.42;
      soft(e.glowRenderer, x, y, z, scale * 1.25, glowRgb, glowAmount, 150);
      Draw3D.shape(e.glowRenderer, Shapes.CARD, x, y, z, rX * sx * 1.25, rY * sx * 1.25, rZ * sx * 1.25, uX * sy * 1.2, uY * sy * 1.2, uZ * sy * 1.2,
         Fx.alpha(glowRgb, (int)Math.min(255, 120 * glowAmount)), glowRgb & 0xFFFFFF);
      int face = Fx.shade(0xFFFBF8F1, light), faceEdge = Fx.shade(0xFFE7E1D3, light);
      Draw3D.shape(e.solidRenderer, Shapes.CARD, x, y, z, rX * sx, rY * sx, rZ * sx, uX * sy, uY * sy, uZ * sy, face, faceEdge);
      int suit = this.variant[i];
      boolean red = suit < 2;
      int pip = Fx.shade(red ? 0xFF000000 | main : 0xFF1A1A1E, light);
      float[] shape = switch (suit) {
         case 0 -> Shapes.HEART;
         case 1 -> Shapes.DIAMOND;
         case 2 -> Shapes.SPADE;
         default -> Shapes.CLUB;
      };
      for (int side = -1; side <= 1; side += 2) {
         double cx = x + nX * lift * side, cy = y + nY * lift * side, cz = z + nZ * lift * side;
         // inner frame line
         Draw3D.shape(e.solidRenderer, Shapes.CARD, cx, cy, cz, rX * sx * 0.86, rY * sx * 0.86, rZ * sx * 0.86, uX * sy * 0.9, uY * sy * 0.9, uZ * sy * 0.9,
            Fx.shade(0xFFF3E6E6, light), Fx.shade(red ? 0xFFF0C8CC : 0xFFD8D8DC, light));
         double cx2 = cx + nX * lift * side, cy2 = cy + nY * lift * side, cz2 = cz + nZ * lift * side;
         Draw3D.shapeAt(e.solidRenderer, shape, cx2, cy2, cz2, rX * sx, rY * sx, rZ * sx, uX * sy, uY * sy, uZ * sy, 0.0, 0.0, 0.42, pip, pip);
         Draw3D.shapeAt(e.solidRenderer, shape, cx2, cy2, cz2, rX * sx, rY * sx, rZ * sx, uX * sy, uY * sy, uZ * sy, -0.22, 0.36, 0.12, pip, pip);
         Draw3D.shapeAt(e.solidRenderer, shape, cx2, cy2, cz2, rX * sx, rY * sx, rZ * sx, -uX * sy, -uY * sy, -uZ * sy, 0.22, 0.36, 0.12, pip, pip);
      }
   }

   private void drawPetal(
      Render3DEvent e, double x, double y, double z, double rX, double rY, double rZ, double uX, double uY, double uZ, double scale, int main, int glowRgb,
      float glowAmount, float light
   ) {
      soft(e.glowRenderer, x, y, z, scale * 1.6, glowRgb, glowAmount, 140);
      int centre = Fx.shade(0xFFFFF1F6, light);
      int edge = Fx.shade(0xFF000000 | main, light);
      // two petals crossed slightly read as one curled blossom petal from every side
      Draw3D.shape(e.solidRenderer, Shapes.PETAL, x, y, z, rX * scale, rY * scale, rZ * scale, uX * scale, uY * scale, uZ * scale, centre, edge);
      double k = 0.35;
      double nX = rY * uZ - rZ * uY, nY = rZ * uX - rX * uZ, nZ = rX * uY - rY * uX;
      Draw3D.shape(e.solidRenderer, Shapes.PETAL, x, y, z, (rX + nX * k) * scale * 0.8, (rY + nY * k) * scale * 0.8, (rZ + nZ * k) * scale * 0.8,
         uX * scale * 0.85, uY * scale * 0.85, uZ * scale * 0.85, centre, Fx.shade(edge, 1.08F));
   }

   private void drawChip(Render3DEvent e, int i, double x, double y, double z, float spin, float tilt, double radius, int glowRgb, float glowAmount, float light) {
      int base = this.customColors.get() ? 0xFF000000 | this.mainRgb() : CHIP_COLORS[i % CHIP_COLORS.length];
      soft(e.glowRenderer, x, y, z, radius * 2.0, glowRgb, glowAmount, 160);
      // chip axis: lies mostly flat, wobbling and spinning around its own axis
      double tiltA = 0.5 + tilt;
      double axX = Math.sin(tiltA) * Math.cos(spin * 0.3), axY = Math.cos(tiltA), axZ = Math.sin(tiltA) * Math.sin(spin * 0.3);
      // two in-plane unit vectors
      double aX = -axY, aY = axX, aZ = 0.0;
      double al = Math.sqrt(aX * aX + aY * aY);
      aX /= al;
      aY /= al;
      double bX = axY * aZ - axZ * aY, bY = axZ * aX - axX * aZ, bZ = axX * aY - axY * aX;
      double half = radius * 0.13;
      int segments = 24;
      for (int side = -1; side <= 1; side += 2) {
         double fx = x + axX * half * side, fy = y + axY * half * side, fz = z + axZ * half * side;
         float faceLight = light * (side > 0 ? 1.0F : 0.7F);
         // striped outer band
         for (int k = 0; k < segments; k++) {
            double a0 = spin + Math.PI * 2.0 * k / segments, a1 = spin + Math.PI * 2.0 * (k + 1) / segments;
            int c = Fx.shade((k & 1) == 0 && k % 3 != 2 ? base : 0xFFF2F2F2, faceLight);
            this.band(e.solidRenderer, side, fx, fy, fz, aX, aY, aZ, bX, bY, bZ, radius * 0.78, radius, a0, a1, c);
            this.band(e.solidRenderer, side, fx, fy, fz, aX, aY, aZ, bX, bY, bZ, radius * 0.58, radius * 0.78, a0, a1, Fx.shade(base, faceLight));
            this.band(e.solidRenderer, side, fx, fy, fz, aX, aY, aZ, bX, bY, bZ, radius * 0.52, radius * 0.58, a0, a1, Fx.shade(0xFFF2F2F2, faceLight));
         }
         Draw3D.shape(e.solidRenderer, Shapes.CIRCLE_24, fx, fy, fz, aX * radius * 1.04, aY * radius * 1.04, aZ * radius * 1.04, bX * radius * 1.04,
            bY * radius * 1.04, bZ * radius * 1.04, Fx.shade(Fx.lerp(base, 0xFFFFFFFF, 0.25F), faceLight), Fx.shade(base, faceLight));
      }
      // rim
      for (int k = 0; k < segments; k++) {
         double a0 = spin + Math.PI * 2.0 * k / segments, a1 = spin + Math.PI * 2.0 * (k + 1) / segments;
         double c0 = Math.cos(a0) * radius, s0 = Math.sin(a0) * radius, c1 = Math.cos(a1) * radius, s1 = Math.sin(a1) * radius;
         double p0x = x + aX * c0 + bX * s0, p0y = y + aY * c0 + bY * s0, p0z = z + aZ * c0 + bZ * s0;
         double p1x = x + aX * c1 + bX * s1, p1y = y + aY * c1 + bY * s1, p1z = z + aZ * c1 + bZ * s1;
         int c = Fx.shade((k & 1) == 0 ? base : 0xFFF2F2F2, light * 0.85F);
         Draw3D.quad(e.solidRenderer, p0x - axX * half, p0y - axY * half, p0z - axZ * half, p1x - axX * half, p1y - axY * half, p1z - axZ * half,
            p1x + axX * half, p1y + axY * half, p1z + axZ * half, p0x + axX * half, p0y + axY * half, p0z + axZ * half, c, c, c, c);
      }
   }

   /** One segment of an annulus in the chip's plane, lifted a hair off the face so it never z-fights. */
   private void band(
      Renderer3D r, int side, double cx, double cy, double cz, double aX, double aY, double aZ, double bX, double bY, double bZ, double inner, double outer, double a0, double a1,
      int color
   ) {
      double nX = aY * bZ - aZ * bY, nY = aZ * bX - aX * bZ, nZ = aX * bY - aY * bX;
      double lift = 0.002 * side;
      cx += nX * lift;
      cy += nY * lift;
      cz += nZ * lift;
      double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
      Draw3D.quad(
         r,
         cx + (aX * c0 + bX * s0) * inner, cy + (aY * c0 + bY * s0) * inner, cz + (aZ * c0 + bZ * s0) * inner,
         cx + (aX * c0 + bX * s0) * outer, cy + (aY * c0 + bY * s0) * outer, cz + (aZ * c0 + bZ * s0) * outer,
         cx + (aX * c1 + bX * s1) * outer, cy + (aY * c1 + bY * s1) * outer, cz + (aZ * c1 + bZ * s1) * outer,
         cx + (aX * c1 + bX * s1) * inner, cy + (aY * c1 + bY * s1) * inner, cz + (aZ * c1 + bZ * s1) * inner,
         color, color, color, color
      );
   }
}
