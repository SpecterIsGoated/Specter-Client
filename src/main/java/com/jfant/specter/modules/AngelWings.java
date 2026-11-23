package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
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
import com.jfant.specter.cosmetic.Wearer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Angel Wings: turns your elytra into big feathered wings.
 *
 * <p>Each wing is a three-bone arm (shoulder - elbow - wrist - tip) carrying three rows of feathers: long primaries on the
 * hand that fan out towards the tip, secondaries on the forearm, and two rows of short coverts over the bones. The bones
 * blend between a folded pose (wings tucked behind the back) and a spread pose, and the whole wing flaps around the
 * shoulder. How open and how fast they flap follows what you do: folded when standing, a slow breath when idle, a
 * flutter when jumping/falling, wide and gliding in elytra flight (strong beats when climbing). Everything is eased
 * per frame so changes are smooth. In flight the wings follow the body's pitch.
 *
 * <p>Angel: white feathers with a soft light and the odd mote. Fallen Angel: dark, ragged feathers (some short, some
 * missing, crooked), dark ash drifting off and a faint ember edge. Drawn opaque (correct depth with your model and the
 * world); the vanilla elytra model is hidden while this is on (see {@code WingsLayerMixin}). Only you see them.
 */
public class AngelWings extends Module {
   public enum Kind {
      Angel,
      FallenAngel
   }

   public enum When {
      WithElytra,
      Always,
      WhileFlying
   }

   private static final int PRIMARIES = 11;
   private static final int SECONDARIES = 10;
   private static final int COVERTS = 9;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Kind> kind = this.sgGeneral.add(new EnumSetting.Builder<Kind>().name("wings").description("Which wings.").defaultValue(Kind.Angel).build());
   private final Setting<When> when = this.sgGeneral
      .add(
         new EnumSetting.Builder<When>()
            .name("show")
            .description("With Elytra: only while an elytra is on your chest (it replaces it). Always: always. While Flying: only during elytra flight.")
            .defaultValue(When.WithElytra)
            .build()
      );
   private final Setting<Double> size = this.sgGeneral
      .add(new DoubleSetting.Builder().name("size").description("Wing size.").defaultValue(1.0).range(0.5, 1.8).sliderRange(0.5, 1.6).build());
   private final Setting<Double> flapSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("flap-speed").description("How fast they beat.").defaultValue(1.0).range(0.3, 2.5).sliderRange(0.3, 2.0).build());
   private final Setting<Double> glow = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow").description("Soft light (Angel) / ember edge (Fallen). 0 for none.").defaultValue(1.0).range(0.0, 2.0).sliderRange(0.0, 2.0).build());
   private final Setting<Boolean> particles = this.sgGeneral
      .add(new BoolSetting.Builder().name("particles").description("Light motes (Angel) or dark ash (Fallen) drifting off the wings.").defaultValue(true).build());
   private final Setting<Boolean> customColor = this.sgGeneral
      .add(new BoolSetting.Builder().name("custom-colour").description("Tint the feathers your own colour.").defaultValue(false).build());
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("feather-colour")
            .description("Feather colour.")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(this.customColor::get)
            .build()
      );

   // animation state (eased each frame)
   private float open = 0.1F;
   private float flap;
   private float flapAmp;
   private float flight;
   private long lastFrame;
   private int ticks;
   private final RandomSource random = RandomSource.create();
   private final ParticleSystem fx = new ParticleSystem(400);
   /** Per-feather raggedness for the fallen wings: length scale (0 = missing) and crook angle. */
   private final float[] rag = new float[64];
   private final float[] crook = new float[64];

   // scratch joints (body frame -> world), reused
   private final double[] sh = new double[3], el = new double[3], wr = new double[3], tp = new double[3];
   private double upX, upY, upZ, rtX, rtY, rtZ, bkX, bkY, bkZ;
   private final double[] r1 = new double[2], r2 = new double[2], r3 = new double[2];
   private float openNow;

   public AngelWings() {
      super(SpecterClientAddon.VISUALS, "angel-wings", "Turns your elytra into animated feathered wings: white Angel wings or ragged Fallen Angel wings. Only you see them.");
      RandomSource r = RandomSource.create(0xFA11E7L);
      for (int i = 0; i < this.rag.length; i++) {
         float roll = r.nextFloat();
         this.rag[i] = roll < 0.12F ? 0.0F : (roll < 0.35F ? 0.45F + r.nextFloat() * 0.3F : 0.85F + r.nextFloat() * 0.25F);
         this.crook[i] = (r.nextFloat() - 0.5F) * 0.45F;
      }
   }

   /** Whether the vanilla elytra should be hidden right now (the wings are drawn instead). */
   public static boolean hidesElytra() {
      AngelWings wings = Modules.get().get(AngelWings.class);
      return wings != null && wings.isActive() && wings.shown();
   }

   private boolean shown() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return false;
      boolean elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
      return switch (this.when.get()) {
         case Always -> true;
         case WithElytra -> elytra;
         case WhileFlying -> mc.player.isFallFlying();
      };
   }

   @Override
   public void onActivate() {
      this.fx.clear();
      this.lastFrame = 0L;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.fx.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level == null) return;
      this.ticks++;
      this.fx.tick(this.mc.level);
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!Wearer.update(event.tickDelta) || !this.shown() || this.mc.player.isInvisible()) {
         this.fx.render(event.solidRenderer, event.depthRenderer, event.glowRenderer, event.tickDelta, 1.0F);
         return;
      }
      Fx.camera();
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      this.animate(dt);
      if (Wearer.thirdPerson) {
         this.frame(event.tickDelta);
         float light = Fx.light(this.mc.level, Wearer.x, Wearer.y + 1.2, Wearer.z);
         for (int side = -1; side <= 1; side += 2) this.wing(event, side, light);
      }
      this.fx.render(event.solidRenderer, event.depthRenderer, event.glowRenderer, event.tickDelta, this.glow.get().floatValue());
   }

   /** Targets from what the player is doing, eased towards. */
   private void animate(float dt) {
      float targetOpen, amp, freq;
      if (Wearer.flying) {
         targetOpen = 1.0F;
         boolean climbing = Wearer.climb > 0.05F;
         amp = climbing ? 0.42F : 0.1F;
         freq = climbing ? 6.0F : 1.8F;
      } else if (!Wearer.onGround && !Wearer.swimming) {
         targetOpen = 0.78F;
         amp = 0.38F;
         freq = 9.0F;
      } else if (Wearer.sneaking) {
         targetOpen = 0.0F;
         amp = 0.0F;
         freq = 1.0F;
      } else if (Wearer.speed > 0.08F) {
         targetOpen = 0.3F;
         amp = 0.05F;
         freq = 4.0F;
      } else {
         targetOpen = 0.24F;
         amp = 0.035F;
         freq = 1.3F;
      }
      this.open = Fx.approach(this.open, targetOpen, 3.5F, dt);
      this.flapAmp = Fx.approach(this.flapAmp, amp, 4.0F, dt);
      this.flight = Fx.approach(this.flight, Wearer.flying || Wearer.swimming ? 1.0F : 0.0F, 3.0F, dt);
      this.flap += dt * freq * this.flapSpeed.get().floatValue();
   }

   /** Body frame (up, right, back), pitched forward with the body in elytra flight. */
   private void frame(float pt) {
      double yaw = Wearer.bodyYaw;
      this.rtX = -Math.cos(yaw);
      this.rtY = 0.0;
      this.rtZ = -Math.sin(yaw);
      double uX = 0.0, uY = 1.0, uZ = 0.0;
      if (this.flight > 0.001F) {
         Vec3 look = this.mc.player.getViewVector(pt);
         // the body lies along the look direction in flight; blend towards it
         uX = Mth.lerp(this.flight, 0.0, look.x);
         uY = Mth.lerp(this.flight, 1.0, look.y);
         uZ = Mth.lerp(this.flight, 0.0, look.z);
         double l = Math.sqrt(uX * uX + uY * uY + uZ * uZ);
         uX /= l;
         uY /= l;
         uZ /= l;
         // right stays horizontal and perpendicular to the look: look x world-up
         double rl = Math.sqrt(look.z * look.z + look.x * look.x);
         if (rl > 1.0E-4 && this.flight > 0.5F) {
            this.rtX = -look.z / rl;
            this.rtZ = look.x / rl;
         }
      }
      this.upX = uX;
      this.upY = uY;
      this.upZ = uZ;
      // forward = up x right; back = -forward
      double fwX = uY * this.rtZ - uZ * this.rtY, fwY = uZ * this.rtX - uX * this.rtZ, fwZ = uX * this.rtY - uY * this.rtX;
      this.bkX = -fwX;
      this.bkY = -fwY;
      this.bkZ = -fwZ;
   }

   /** Body-frame point (out = sideways, up, back) relative to the shoulder pivot, into world space. */
   private void toWorld(double baseX, double baseY, double baseZ, double out, double up, double back, double[] dst) {
      dst[0] = baseX + this.rtX * out + this.upX * up + this.bkX * back;
      dst[1] = baseY + this.rtY * out + this.upY * up + this.bkY * back;
      dst[2] = baseZ + this.rtZ * out + this.upZ * up + this.bkZ * back;
   }

   private void wing(Render3DEvent e, int side, float light) {
      float s = this.size.get().floatValue();
      boolean fallen = this.kind.get() == Kind.FallenAngel;
      double shoulderUp = Wearer.sneaking && this.flight < 0.5F ? 1.05 : 1.28;
      double bx = Wearer.x + this.upX * shoulderUp + this.bkX * 0.17 + this.rtX * 0.1 * side;
      double by = Wearer.y + this.upY * shoulderUp + this.bkY * 0.17 + this.rtY * 0.1 * side;
      double bz = Wearer.z + this.upZ * shoulderUp + this.bkZ * 0.17 + this.rtZ * 0.1 * side;
      float o = Fx.smooth(this.open);
      // joint positions in the body frame (x = out, y = up, z = back), folded -> spread
      // folded: the wrist peaks above the shoulder and the hand sweeps down behind the back (classic angel silhouette)
      double ex = Mth.lerp(o, 0.2, 0.4), ey = Mth.lerp(o, 0.22, 0.25), ez = Mth.lerp(o, 0.12, 0.1);
      double wx = Mth.lerp(o, 0.3, 0.86), wy = Mth.lerp(o, 0.52, 0.45), wz = Mth.lerp(o, 0.2, 0.15);
      double tx = Mth.lerp(o, 0.16, 1.52), ty = Mth.lerp(o, -0.95, 0.3), tz = Mth.lerp(o, 0.28, 0.2);
      // flap: rotate the arm around the back axis at the shoulder; wrist trails the beat a little
      double beat = Math.sin(this.flap * Math.PI * 2.0) * this.flapAmp;
      double lag = Math.sin(this.flap * Math.PI * 2.0 - 0.7) * this.flapAmp;
      double[] e1 = rotate(ex, ey, beat, this.r1), w1 = rotate(wx, wy, beat * 0.6 + lag * 0.4, this.r2), t1 = rotate(tx, ty, beat * 0.3 + lag * 0.7, this.r3);
      this.openNow = o;
      double sx = side * s;
      this.toWorld(bx, by, bz, 0.0, 0.0, 0.0, this.sh);
      this.toWorld(bx, by, bz, e1[0] * sx, e1[1] * s, ez * s, this.el);
      this.toWorld(bx, by, bz, w1[0] * sx, w1[1] * s, wz * s, this.wr);
      this.toWorld(bx, by, bz, t1[0] * sx, t1[1] * s, tz * s, this.tp);
      // the wing surface faces backwards, folded or spread (the flap turns the arm around that same axis)
      double nX = this.bkX, nY = this.bkY, nZ = this.bkZ;

      int feather = this.customColor.get() ? this.color.get().getPacked() | 0xFF000000 : (fallen ? 0xFF2B2731 : 0xFFFFFFFF);
      int featherEdge = fallen ? 0xFF121016 : Fx.lerp(feather, 0xFFC9CFDC, 0.35F);
      float g = this.glow.get().floatValue();
      int idx = side > 0 ? 0 : 32;

      // primaries on the hand (wrist -> tip), fanning out towards the tip
      for (int i = 0; i < PRIMARIES; i++) {
         double u = (i + 0.5) / PRIMARIES;
         double len = (0.5 + 0.3 * Math.sin(u * Math.PI * 0.9)) * s;
         this.feather(e, this.wr, this.tp, u, nX, nY, nZ, len, 0.15 * s, 0.72 * u, -0.012, feather, featherEdge, light * 0.93F, fallen, idx + i, side);
      }
      // secondaries on the forearm
      for (int i = 0; i < SECONDARIES; i++) {
         double u = (i + 0.5) / SECONDARIES;
         double len = (0.44 + 0.06 * Math.sin(u * Math.PI)) * s * Mth.lerp(o, 0.8F, 1.0F);
         this.feather(e, this.el, this.wr, u, nX, nY, nZ, len, 0.16 * s, 0.05, 0.0, feather, featherEdge, light * 0.97F, fallen, idx + 12 + i, side);
      }
      // coverts over the bones (two rows), short and bright
      for (int i = 0; i < COVERTS; i++) {
         double u = (i + 0.5) / COVERTS;
         boolean onHand = u > 0.5;
         double[] a = onHand ? this.el : this.sh, b = onHand ? this.wr : this.el;
         double uu = onHand ? (u - 0.5) * 2.0 : u * 2.0;
         this.feather(e, a, b, uu, nX, nY, nZ, 0.24 * s, 0.12 * s, 0.1, 0.012, Fx.lerp(feather, 0xFFFFFFFF, 0.08F), featherEdge, light, fallen, idx + 22 + i, side);
         this.feather(e, this.sh, this.wr, u, nX, nY, nZ, 0.14 * s, 0.1 * s, 0.1, 0.024, Fx.lerp(feather, 0xFFFFFFFF, 0.12F), featherEdge, light, fallen, -1, side);
      }

      if (g > 0.0F) {
         double gr = 0.55 * s * (0.6 + o * 0.6);
         int glowRgb = fallen ? 0x9E2A3A : (this.customColor.get() ? feather & 0xFFFFFF : 0xEAF2FF);
         int a = (int)Math.min(255, (fallen ? 60 : 85) * g);
         Draw3D.glow(e.glowRenderer, (this.el[0] + this.wr[0]) * 0.5, (this.el[1] + this.wr[1]) * 0.5 - 0.1, (this.el[2] + this.wr[2]) * 0.5,
            Fx.rightX * gr, Fx.rightY * gr, Fx.rightZ * gr, Fx.upX * gr, Fx.upY * gr, Fx.upZ * gr, Fx.alpha(glowRgb, a));
         Draw3D.glow(e.glowRenderer, (this.wr[0] + this.tp[0]) * 0.5, (this.wr[1] + this.tp[1]) * 0.5 - 0.15, (this.wr[2] + this.tp[2]) * 0.5,
            Fx.rightX * gr, Fx.rightY * gr, Fx.rightZ * gr, Fx.upX * gr, Fx.upY * gr, Fx.upZ * gr, Fx.alpha(glowRgb, a));
      }

      if (this.particles.get() && this.random.nextInt(fallen ? 4 : 7) == 0) {
         double u = this.random.nextDouble();
         double px = Mth.lerp(u, this.wr[0], this.tp[0]), py = Mth.lerp(u, this.wr[1], this.tp[1]) - 0.2, pz = Mth.lerp(u, this.wr[2], this.tp[2]);
         ParticleSystem.Spec spec = this.fx.spec.reset();
         if (fallen) {
            spec.shape = Shapes.ID_DOT;
            spec.flags = ParticleSystem.BILLBOARD;
            spec.life = 30 + this.random.nextInt(25);
            spec.size0 = 0.05F;
            spec.size1 = 0.11F;
            spec.color0 = 0xC0141218;
            spec.color1 = 0x00302A36;
            spec.drag = 0.93F;
            spec.gravity = -0.0012F;
            this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.02F, 0.005F, (this.random.nextFloat() - 0.5F) * 0.02F);
            if (g > 0.0F && this.random.nextInt(3) == 0) {
               spec.reset();
               spec.shape = Shapes.ID_SPARK;
               spec.flags = ParticleSystem.BILLBOARD | ParticleSystem.ADDITIVE | ParticleSystem.SHRINK;
               spec.life = 14;
               spec.size0 = 0.06F;
               spec.color0 = 0xE0FF5A3C;
               spec.gravity = 0.002F;
               this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.03F, 0.0, (this.random.nextFloat() - 0.5F) * 0.03F);
            }
         } else {
            spec.shape = Shapes.ID_SPARK;
            spec.flags = ParticleSystem.BILLBOARD | ParticleSystem.ADDITIVE | ParticleSystem.GLOW | ParticleSystem.SHRINK;
            spec.life = 18 + this.random.nextInt(14);
            spec.size0 = 0.07F;
            spec.color0 = 0xF0FFFFFF;
            spec.glow = 0x50E8F0FF;
            spec.drag = 0.92F;
            spec.gravity = 0.0015F;
            spec.spinV = 0.15F;
            this.fx.spawn(px, py, pz, (this.random.nextFloat() - 0.5F) * 0.02F, -0.005F, (this.random.nextFloat() - 0.5F) * 0.02F);
         }
      }
   }

   /** Rotates (out, up) by angle a around the back axis (positive raises the wing). */
   private static double[] rotate(double out, double up, double a, double[] dst) {
      double c = Math.cos(a), s = Math.sin(a);
      dst[0] = out * c - up * s;
      dst[1] = out * s + up * c;
      return dst;
   }

   /**
    * One feather rooted on the bone a->b at u, hanging along the trailing direction (bone x normal), turned towards the
    * bone by {@code fan} (primaries splay out), lifted {@code layer} off the wing so rows overlap without flicker.
    */
   private void feather(
      Render3DEvent e, double[] a, double[] b, double u, double nX, double nY, double nZ, double length, double width, double fan, double layer, int centre, int edge,
      float light, boolean fallen, int id, int side
   ) {
      double rootX = Mth.lerp(u, a[0], b[0]), rootY = Mth.lerp(u, a[1], b[1]), rootZ = Mth.lerp(u, a[2], b[2]);
      double dX = b[0] - a[0], dY = b[1] - a[1], dZ = b[2] - a[2];
      double dl = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
      if (dl < 1.0E-5) return;
      dX /= dl;
      dY /= dl;
      dZ /= dl;
      // trailing direction = bone x normal (points away from the leading edge)
      double tX = dY * nZ - dZ * nY, tY = dZ * nX - dX * nZ, tZ = dX * nY - dY * nX;
      // feathers trail down when spread and back when folded, whichever way the bone runs
      double o = this.openNow;
      // spread: feathers hang down; folded: they lie across the back towards the spine
      double wantX = -this.upX * (0.2 + 0.8 * o) - this.rtX * side * (1.0 - o);
      double wantY = -this.upY * (0.2 + 0.8 * o) - this.rtY * side * (1.0 - o);
      double wantZ = -this.upZ * (0.2 + 0.8 * o) - this.rtZ * side * (1.0 - o);
      if (tX * wantX + tY * wantY + tZ * wantZ < 0.0) {
         tX = -tX;
         tY = -tY;
         tZ = -tZ;
      }
      double fX = tX * (1.0 - fan) + dX * fan, fY = tY * (1.0 - fan) + dY * fan, fZ = tZ * (1.0 - fan) + dZ * fan;
      if (fallen && id >= 0) {
         double c = this.crook[id];
         fX += dX * c;
         fY += dY * c;
         fZ += dZ * c;
         length *= this.rag[id];
         if (length <= 0.01) return;
      }
      double fl = Math.sqrt(fX * fX + fY * fY + fZ * fZ);
      fX /= fl;
      fY /= fl;
      fZ /= fl;
      // folded wings hang as a layered curtain: feathers point down (a little in towards the spine), blending to the
      // spread direction as the wing opens
      double hX = -this.upX * 0.92 - this.rtX * side * 0.3, hY = -this.upY * 0.92 - this.rtY * side * 0.3, hZ = -this.upZ * 0.92 - this.rtZ * side * 0.3;
      double hl = Math.sqrt(hX * hX + hY * hY + hZ * hZ);
      fX = Mth.lerp(o, hX / hl, fX);
      fY = Mth.lerp(o, hY / hl, fY);
      fZ = Mth.lerp(o, hZ / hl, fZ);
      fl = Math.sqrt(fX * fX + fY * fY + fZ * fZ);
      if (fl < 1.0E-4) return;
      fX /= fl;
      fY /= fl;
      fZ /= fl;
      // folded, the hand already reaches the calves: keep its feathers off the ground
      if (fan > 0.2) length *= Mth.lerp(o, 0.5, 1.0);
      // feather plane: along f, width across the wing surface
      double wX = nY * fZ - nZ * fY, wY = nZ * fX - nX * fZ, wZ = nX * fY - nY * fX;
      double cx = rootX + fX * length * 0.5 + nX * layer, cy = rootY + fY * length * 0.5 + nY * layer, cz = rootZ + fZ * length * 0.5 + nZ * layer;
      Draw3D.shape(
         e.solidRenderer, Shapes.FEATHER, cx, cy, cz, wX * width * 2.0, wY * width * 2.0, wZ * width * 2.0, fX * length, fY * length, fZ * length,
         Fx.shade(centre, light), Fx.shade(edge, light)
      );
      // rachis (quill): a thin lighter strip down the middle
      double q = width * 0.05;
      int quill = Fx.shade(fallen ? 0xFF4A4452 : 0xFFF4F6FA, light);
      Draw3D.quad(e.solidRenderer,
         rootX + nX * (layer + 0.002) - wX * q, rootY + nY * (layer + 0.002) - wY * q, rootZ + nZ * (layer + 0.002) - wZ * q,
         rootX + nX * (layer + 0.002) + wX * q, rootY + nY * (layer + 0.002) + wY * q, rootZ + nZ * (layer + 0.002) + wZ * q,
         rootX + fX * length * 0.92 + nX * (layer + 0.002), rootY + fY * length * 0.92 + nY * (layer + 0.002), rootZ + fZ * length * 0.92 + nZ * (layer + 0.002),
         rootX + fX * length * 0.92 + nX * (layer + 0.002), rootY + fY * length * 0.92 + nY * (layer + 0.002), rootZ + fZ * length * 0.92 + nZ * (layer + 0.002),
         quill, quill, quill, quill);
   }
}
