package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.meteor.KeyInputEvent;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.ping.PingMath;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;

/**
 * Fortnite-style pings: press the ping key (middle mouse by default) to drop a marker where you are looking.
 *
 * <p>The marker is a real 3D gem floating over the spot: lit and shaded faces, dark edges, a slow spin and bob, and it
 * gets smaller with distance (but never too small to see), so it reads as an object in the world rather than a sticker
 * on the screen.
 *
 * <p>The ray starts at whatever camera you are actually looking through: Ghost Freecam's floating camera when it is
 * on, Ghost Freelook's free-turning camera, otherwise your own eyes. Pings are world anchored (or follow the mob/player
 * you pinged), so turning Freecam on or off never moves them. Pinging a ping again removes it.
 *
 * <p>Pings are client side only - nobody else sees them.
 */
public class PingSystem extends Module {
   private static final int RING_SEGMENTS = 32;
   private static final double[] RING_COS = new double[RING_SEGMENTS + 1];
   private static final double[] RING_SIN = new double[RING_SEGMENTS + 1];
   private static final long POP_MS = 360L;
   private static final long RING_MS = 450L;
   private static final long PULSE_MS = 750L;
   private static final long FADE_MS = 600L;
   private static final long IDLE_PULSE_EVERY_MS = 2200L;
   private static final int EDGE_MARGIN = 26;
   /** Light comes from above, a little from the side, so the gem's faces each get their own shade. */
   private static final double LX = 0.35;
   private static final double LY = 0.85;
   private static final double LZ = 0.40;

   static {
      for (int i = 0; i <= RING_SEGMENTS; i++) {
         double a = Math.PI * 2.0 * i / RING_SEGMENTS;
         RING_COS[i] = Math.cos(a);
         RING_SIN[i] = Math.sin(a);
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgLook = this.settings.createGroup("Appearance");

   private final Setting<Keybind> pingKey = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("ping-key")
            .description("Press to ping where you are looking. Ping a ping again to remove it. Middle mouse by default; any key or mouse button works.")
            .defaultValue(Keybind.fromButton(InputConstants.MOUSE_BUTTON_MIDDLE))
            .build()
      );
   private final Setting<Boolean> replacePickBlock = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("replace-pick-block")
            .description("When the ping key is a mouse button, that click only pings and no longer does its vanilla job (middle mouse = pick block).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> expires = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("expires")
            .description("Pings fade away after a while. Off = they stay until you ping again, remove them, or change world.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> duration = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("duration")
            .description("Seconds a ping stays before it fades away.")
            .defaultValue(10.0)
            .range(1.0, 600.0)
            .sliderRange(2.0, 120.0)
            .visible(this.expires::get)
            .build()
      );
   private final Setting<Integer> maxDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-distance")
            .description("Farthest block you can ping, in blocks. Only loaded chunks can be pinged.")
            .defaultValue(256)
            .range(8, 1024)
            .sliderRange(16, 512)
            .build()
      );
   private final Setting<Integer> maxPings = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-pings")
            .description("How many pings can be up at once. A new one replaces the oldest.")
            .defaultValue(1)
            .range(1, 10)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Boolean> entities = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ping-entities")
            .description("Pinging a mob or player marks it and the ping follows it around.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> sound = this.sgGeneral
      .add(new BoolSetting.Builder().name("sound").description("Play a soft ping sound (only you hear it).").defaultValue(true).build());
   private final Setting<SettingColor> color = this.sgLook
      .add(new ColorSetting.Builder().name("color").description("Ping colour.").defaultValue(new SettingColor(255, 196, 40, 255)).build());
   private final Setting<Boolean> animation = this.sgLook
      .add(
         new BoolSetting.Builder()
            .name("animation")
            .description("The gem pops in, spins and bobs, a ring ripples out when placed, and a soft pulse repeats while it is up.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> edgeArrows = this.sgLook
      .add(
         new BoolSetting.Builder()
            .name("off-screen-markers")
            .description("Pings behind you or off screen show a marker at the edge of the screen, pointing the way.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> outline = this.sgLook
      .add(new BoolSetting.Builder().name("outline").description("Outline the exact block or mob that was pinged, through walls.").defaultValue(true).build());
   private final Setting<Double> scale = this.sgLook
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the gem and the off-screen marker.")
            .defaultValue(1.0)
            .range(0.5, 2.5)
            .sliderRange(0.5, 2.0)
            .build()
      );

   private final List<PingSystem.Ping> pings = new ArrayList<>();
   private ClientLevel level;
   // Reused every frame - no allocations while drawing.
   private final Vector3d screen = new Vector3d();
   private final double[] edge = new double[2];
   private final double[] gx = new double[6];
   private final double[] gy = new double[6];
   private final double[] gz = new double[6];
   private final boolean[] topVisible = new boolean[4];
   private final boolean[] bottomVisible = new boolean[4];
   private final Color side = new Color();
   private final Color line = new Color();
   private final Color sideTop = new Color();
   private final Color face = new Color();

   public PingSystem() {
      super(
         SpecterClientAddon.TOOLS,
         "ping-system",
         "Fortnite-style pings: middle-click to drop a 3D marker where you are looking. Works through Ghost Freecam. Only you see them."
      );
   }

   @Override
   public void onActivate() {
      this.pings.clear();
      this.level = this.mc.level;
   }

   @Override
   public void onDeactivate() {
      this.pings.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.pings.clear();
      this.level = null;
   }

   // ---------------------------------------------------------------- input

   @EventHandler
   private void onMouse(MouseClickEvent event) {
      if (event.action == KeyAction.Press && this.canPing() && this.pingKey.get().matches(event.input)) {
         this.ping();
         if (this.replacePickBlock.get()) {
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onKey(KeyInputEvent event) {
      if (event.action == KeyAction.Press && this.canPing() && this.pingKey.get().isKey() && this.pingKey.get().matches(event.input)) {
         this.ping();
      }
   }

   private boolean canPing() {
      return this.mc.player != null && this.mc.level != null && this.mc.gui.screen() == null;
   }

   // ---------------------------------------------------------------- placing

   /** Casts from the camera you are actually looking through and drops (or removes) a ping on what it hits. */
   public void ping() {
      Vec3 origin;
      Vec3 dir;
      GhostFreecam freecam = GhostFreecam.on();
      if (freecam != null) {
         // The floating camera - never the body standing somewhere else.
         origin = new Vec3(freecam.x, freecam.y, freecam.z);
         dir = Vec3.directionFromRotation(freecam.pitch, freecam.yaw);
      } else {
         GhostFreelook look = GhostFreelook.on();
         if (look != null) {
            origin = this.mc.gameRenderer.mainCamera().position();
            dir = Vec3.directionFromRotation(look.pitch, look.yaw);
         } else {
            float pt = this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            origin = this.mc.player.getEyePosition(pt);
            dir = this.mc.player.getViewVector(pt);
         }
      }

      HitResult hit = this.cast(origin, dir, this.maxDistance.get());
      if (hit == null) {
         this.mc.gui.hud.setOverlayMessage(Component.literal("Nothing to ping within " + this.maxDistance.get() + " blocks"), false);
         return;
      }

      PingSystem.Ping ping;
      if (hit instanceof EntityHitResult eh) {
         Entity e = eh.getEntity();
         if (this.removeIfPinged(this.findEntityPing(e))) {
            return;
         }

         ping = new PingSystem.Ping();
         ping.entity = e;
         Vec3 p = e.position();
         ping.x = p.x;
         ping.y = p.y + e.getBbHeight();
         ping.z = p.z;
      } else {
         BlockHitResult bh = (BlockHitResult)hit;
         Vec3 at = bh.getLocation();
         if (this.removeIfPinged(this.findBlockPing(at))) {
            return;
         }

         BlockPos bp = bh.getBlockPos();
         ping = new PingSystem.Ping();
         ping.bx = bp.getX();
         ping.by = bp.getY();
         ping.bz = bp.getZ();
         ping.x = at.x;
         ping.y = at.y;
         ping.z = at.z;
      }

      ping.rx = ping.x;
      ping.ry = ping.y;
      ping.rz = ping.z;
      ping.placedAt = System.currentTimeMillis();
      this.pings.add(ping);

      while (this.pings.size() > this.maxPings.get()) {
         this.pings.remove(0);
      }

      this.playSound(1.7F);
   }

   /** Pinging a ping takes it down. */
   private boolean removeIfPinged(PingSystem.Ping existing) {
      if (existing == null) {
         return false;
      }

      this.pings.remove(existing);
      this.playSound(1.1F);
      return true;
   }

   private void playSound(float pitch) {
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch));
      }
   }

   /** Block first, then the nearest pickable entity in front of it (never yourself). */
   private HitResult cast(Vec3 origin, Vec3 dir, double range) {
      Vec3 end = origin.add(dir.x * range, dir.y * range, dir.z * range);
      BlockHitResult block = this.mc.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));
      boolean blockHit = block != null && block.getType() == HitResult.Type.BLOCK;
      if (blockHit) {
         end = block.getLocation();
      }

      if (this.entities.get()) {
         double reach = origin.distanceTo(end);
         AABB box = new AABB(origin, end).inflate(1.0);
         EntityHitResult entity = ProjectileUtil.getEntityHitResult(
            this.mc.player, origin, end, box, e -> e != this.mc.player && !e.isSpectator() && e.isPickable(), reach * reach
         );
         if (entity != null) {
            return entity;
         }
      }

      return blockHit ? block : null;
   }

   private PingSystem.Ping findEntityPing(Entity e) {
      for (PingSystem.Ping p : this.pings) {
         if (p.entity == e) {
            return p;
         }
      }

      return null;
   }

   private PingSystem.Ping findBlockPing(Vec3 at) {
      for (PingSystem.Ping p : this.pings) {
         if (p.entity == null && PingMath.near(p.x, p.y, p.z, at.x, at.y, at.z, 1.5)) {
            return p;
         }
      }

      return null;
   }

   // ---------------------------------------------------------------- upkeep

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != this.level) {
         // Other world or dimension: old pings point at nothing.
         this.pings.clear();
         this.level = this.mc.level;
      }

      if (!this.pings.isEmpty()) {
         long now = System.currentTimeMillis();
         long life = this.lifeMs();

         for (int i = this.pings.size() - 1; i >= 0; i--) {
            PingSystem.Ping p = this.pings.get(i);
            if (now - p.placedAt >= life) {
               this.pings.remove(i);
            } else if (p.entity != null && p.entity.isRemoved()) {
               // The mob died or left: the ping stays where it was last seen.
               p.entity = null;
               BlockPos bp = BlockPos.containing(p.x, p.y - 0.01, p.z);
               p.bx = bp.getX();
               p.by = bp.getY();
               p.bz = bp.getZ();
            }
         }

         while (this.pings.size() > this.maxPings.get()) {
            this.pings.remove(0);
         }
      }
   }

   /** How long a ping lives; effectively forever when expiring is off. */
   private long lifeMs() {
      return this.expires.get() ? (long)(this.duration.get() * 1000.0) : Long.MAX_VALUE / 4;
   }

   // ---------------------------------------------------------------- world

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.pings.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      long life = this.lifeMs();
      SettingColor c = this.color.get();
      boolean anim = this.animation.get();
      double camX = event.offsetX;
      double camY = event.offsetY;
      double camZ = event.offsetZ;

      for (PingSystem.Ping p : this.pings) {
         long age = now - p.placedAt;
         if (age >= life) {
            continue;
         }

         this.anchor(p, event.tickDelta);
         float alpha = PingMath.fade(age, life, FADE_MS) * (anim ? PingMath.clamp01(age / 120.0F) : 1.0F);
         if (alpha <= 0.01F) {
            continue;
         }

         double x = p.rx;
         double y = p.ry;
         double z = p.rz;
         if (this.outline.get()) {
            this.line.set(c.r, c.g, c.b, (int)(230 * alpha));
            if (p.entity != null) {
               AABB bb = p.entity.getBoundingBox();
               Vec3 now3 = p.entity.position();
               double ox = x - now3.x;
               double oy = y - p.entity.getBbHeight() - now3.y;
               double oz = z - now3.z;
               event.renderer.boxLines(bb.minX + ox, bb.minY + oy, bb.minZ + oz, bb.maxX + ox, bb.maxY + oy, bb.maxZ + oz, this.line, 0);
            } else {
               this.side.set(c.r, c.g, c.b, (int)(38 * alpha));
               event.renderer.box(p.bx, p.by, p.bz, p.bx + 1, p.by + 1, p.bz + 1, this.side, this.line, ShapeMode.Both, 0);
            }
         }

         // Gem size grows slower than distance: it shrinks on screen as it gets farther (depth), but never vanishes.
         double dist = Math.sqrt(PingMath.distSq(camX, camY, camZ, x, y, z));
         double size = PingMath.gemSize(dist) * this.scale.get();
         float pop = anim ? PingMath.easeOutBack(PingMath.clamp01((float)age / POP_MS)) : 1.0F;
         double seconds = age / 1000.0;
         double bob = anim ? Math.sin(seconds * 2.2) * 0.12 * size : 0.0;
         double beamTop = y + 1.2 + size * 1.6;
         double gemCenter = beamTop + size * 1.25 + bob;

         // Beam: two crossed gradient quads, solid at the spot, fading out under the gem.
         double w = 0.03 + size * 0.04;
         this.side.set(c.r, c.g, c.b, (int)(200 * alpha));
         this.sideTop.set(c.r, c.g, c.b, (int)(40 * alpha));
         event.renderer.gradientQuadVertical(x - w, y, z, x + w, beamTop, z, this.sideTop, this.side);
         event.renderer.gradientQuadVertical(x, y, z - w, x, beamTop, z + w, this.sideTop, this.side);

         this.gem(event, x, gemCenter, z, size * Math.max(0.01F, pop), anim ? seconds * 1.3 : Math.PI / 4.0, c, alpha, camX, camY, camZ);

         // Ring on the spot: grows in when placed, then a ripple, then a soft idle pulse.
         double ringY = y + 0.02;
         double r = anim ? 0.25 + 0.3 * PingMath.easeOutCubic(PingMath.clamp01((float)age / RING_MS)) : 0.55;
         this.line.set(c.r, c.g, c.b, (int)(255 * alpha));
         this.ring(event, x, ringY, z, r, this.line);
         if (anim) {
            long phase = age < PULSE_MS ? age : (age - PULSE_MS) % IDLE_PULSE_EVERY_MS;
            boolean first = age < PULSE_MS;
            if (phase < PULSE_MS) {
               float t = (float)phase / PULSE_MS;
               double pr = 0.3 + (first ? 1.9 : 1.1) * PingMath.easeOutCubic(t);
               this.line.set(c.r, c.g, c.b, (int)((first ? 255 : 140) * alpha * (1.0F - t)));
               this.ring(event, x, ringY, z, pr, this.line);
            }
         }
      }
   }

   /**
    * A shaded octahedron (a gem stretched downward into a point). Only the faces turned toward the camera are drawn -
    * the shape is convex, so that alone gives correct hidden-surface removal even through walls - and each face is
    * shaded by how much it faces the light, with dark edges so it stays readable against bright and dark scenes.
    */
   private void gem(Render3DEvent event, double cx, double cy, double cz, double size, double spin, SettingColor c, float alpha, double camX, double camY, double camZ) {
      double r = size * 0.62;
      // 0 = top, 1 = bottom, 2..5 = the waist.
      this.gx[0] = cx;
      this.gy[0] = cy + size * 0.85;
      this.gz[0] = cz;
      this.gx[1] = cx;
      this.gy[1] = cy - size * 1.25;
      this.gz[1] = cz;

      for (int i = 0; i < 4; i++) {
         double a = spin + Math.PI / 2.0 * i;
         this.gx[2 + i] = cx + Math.cos(a) * r;
         this.gy[2 + i] = cy;
         this.gz[2 + i] = cz + Math.sin(a) * r;
      }

      // Faces first; top face i = (top, waist i, waist i+1), bottom face i = (bottom, waist i+1, waist i).
      for (int i = 0; i < 4; i++) {
         int a = 2 + i;
         int b = 2 + (i + 1) % 4;
         this.topVisible[i] = this.face(event, 0, a, b, cx, cy, cz, c, alpha, camX, camY, camZ);
         this.bottomVisible[i] = this.face(event, 1, b, a, cx, cy, cz, c, alpha, camX, camY, camZ);
      }

      // Then the dark edges of the visible faces, as strips in the same (triangle) pass so the faces can't cover them.
      // Lines would be drawn BEFORE all triangles and end up hidden under the gem.
      this.line.set(c.r / 4, c.g / 4, c.b / 4, (int)(225 * alpha));
      double w = size * 0.035;
      for (int i = 0; i < 4; i++) {
         int prev = (i + 3) % 4;
         int a = 2 + i;
         int b = 2 + (i + 1) % 4;
         if (this.topVisible[prev] || this.topVisible[i]) {
            this.edgeStrip(event, 0, a, w, camX, camY, camZ);
         }

         if (this.bottomVisible[prev] || this.bottomVisible[i]) {
            this.edgeStrip(event, 1, a, w, camX, camY, camZ);
         }

         if (this.topVisible[i] || this.bottomVisible[i]) {
            this.edgeStrip(event, a, b, w, camX, camY, camZ);
         }
      }
   }

   /** A thin quad along gem vertices p-q, turned to face the camera. */
   private void edgeStrip(Render3DEvent event, int p, int q, double width, double camX, double camY, double camZ) {
      double dx = this.gx[q] - this.gx[p];
      double dy = this.gy[q] - this.gy[p];
      double dz = this.gz[q] - this.gz[p];
      double vx = camX - (this.gx[p] + this.gx[q]) * 0.5;
      double vy = camY - (this.gy[p] + this.gy[q]) * 0.5;
      double vz = camZ - (this.gz[p] + this.gz[q]) * 0.5;
      double sx = dy * vz - dz * vy;
      double sy = dz * vx - dx * vz;
      double sz = dx * vy - dy * vx;
      double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
      if (len < 1.0E-9) {
         return;
      }

      double k = width * 0.5 / len;
      sx *= k;
      sy *= k;
      sz *= k;
      event.renderer.quad(
         this.gx[p] - sx, this.gy[p] - sy, this.gz[p] - sz,
         this.gx[p] + sx, this.gy[p] + sy, this.gz[p] + sz,
         this.gx[q] + sx, this.gy[q] + sy, this.gz[q] + sz,
         this.gx[q] - sx, this.gy[q] - sy, this.gz[q] - sz,
         this.line
      );
   }

   /** Draws one gem face if it is turned toward the camera. True if it was drawn. */
   private boolean face(Render3DEvent event, int i0, int i1, int i2, double cx, double cy, double cz, SettingColor c, float alpha, double camX, double camY, double camZ) {
      double ax = this.gx[i1] - this.gx[i0];
      double ay = this.gy[i1] - this.gy[i0];
      double az = this.gz[i1] - this.gz[i0];
      double bx = this.gx[i2] - this.gx[i0];
      double by = this.gy[i2] - this.gy[i0];
      double bz = this.gz[i2] - this.gz[i0];
      double nx = ay * bz - az * by;
      double ny = az * bx - ax * bz;
      double nz = ax * by - ay * bx;
      double fx = (this.gx[i0] + this.gx[i1] + this.gx[i2]) / 3.0;
      double fy = (this.gy[i0] + this.gy[i1] + this.gy[i2]) / 3.0;
      double fz = (this.gz[i0] + this.gz[i1] + this.gz[i2]) / 3.0;
      if (nx * (fx - cx) + ny * (fy - cy) + nz * (fz - cz) < 0.0) {
         // Make the normal point outward whatever the winding.
         nx = -nx;
         ny = -ny;
         nz = -nz;
      }

      if (nx * (camX - fx) + ny * (camY - fy) + nz * (camZ - fz) <= 0.0) {
         return false;
      }

      double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (len < 1.0E-9) {
         return false;
      }

      float shade = PingMath.shade(nx / len, ny / len, nz / len, LX, LY, LZ);
      this.face.set(Math.min(255, (int)(c.r * shade)), Math.min(255, (int)(c.g * shade)), Math.min(255, (int)(c.b * shade)), (int)(245 * alpha));
      event.renderer.quad(
         this.gx[i0], this.gy[i0], this.gz[i0], this.gx[i1], this.gy[i1], this.gz[i1], this.gx[i2], this.gy[i2], this.gz[i2], this.gx[i2], this.gy[i2], this.gz[i2], this.face
      );
      return true;
   }

   private void ring(Render3DEvent event, double x, double y, double z, double r, Color color) {
      for (int i = 0; i < RING_SEGMENTS; i++) {
         event.renderer.line(x + RING_COS[i] * r, y, z + RING_SIN[i] * r, x + RING_COS[i + 1] * r, y, z + RING_SIN[i + 1] * r, color);
      }
   }

   /** Where the ping is this frame: fixed, or riding on top of its entity. Written into p.rx/ry/rz. */
   private void anchor(PingSystem.Ping p, float tickDelta) {
      if (p.entity != null && !p.entity.isRemoved()) {
         Vec3 pos = p.entity.getPosition(tickDelta);
         p.x = pos.x;
         p.y = pos.y + p.entity.getBbHeight();
         p.z = pos.z;
      }

      p.rx = p.x;
      p.ry = p.y;
      p.rz = p.z;
   }

   // ---------------------------------------------------------------- off-screen markers

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.pings.isEmpty() || !this.edgeArrows.get() || this.mc.player == null || this.mc.gui.hud.isHidden()) {
         return;
      }

      long now = System.currentTimeMillis();
      long life = this.lifeMs();
      double guiScale = this.mc.getWindow().getGuiScale();
      double userScale = this.scale.get();
      int winW = this.mc.getWindow().getWidth();
      int winH = this.mc.getWindow().getHeight();
      double margin = EDGE_MARGIN * guiScale * userScale;
      SettingColor c = this.color.get();
      GuiGraphicsExtractor g = event.graphics;

      for (PingSystem.Ping p : this.pings) {
         long age = now - p.placedAt;
         if (age >= life) {
            continue;
         }

         float alpha = PingMath.fade(age, life, FADE_MS);
         if (alpha <= 0.01F) {
            continue;
         }

         this.screen.set(p.rx, p.ry + 1.5, p.rz);
         if (!NametagUtils.to2D(this.screen, guiScale * userScale, false, true)) {
            continue;
         }

         boolean behind = this.screen.z < 0.0;
         if (!behind && PingMath.inside(this.screen.x, this.screen.y, winW, winH, margin)) {
            // On screen the 3D gem does the job.
            continue;
         }

         PingMath.clampToEdge(this.screen.x, this.screen.y, winW, winH, margin, this.edge);
         this.screen.x = this.edge[0];
         this.screen.y = this.edge[1];
         NametagUtils.begin(this.screen, g);
         Matrix3x2fStack pose = g.pose();
         pose.pushMatrix();
         this.drawEdgeMarker(g, c, alpha);
         pose.popMatrix();
         NametagUtils.end(g);
      }
   }

   /** A two-tone diamond (lit top half, shaded bottom half) so it matches the 3D gem. */
   private void drawEdgeMarker(GuiGraphicsExtractor g, SettingColor c, float alpha) {
      int r = 6;
      int rim = PingMath.argb((int)(170 * alpha), 10, 10, 14);
      int lit = PingMath.argb((int)(255 * alpha), Math.min(255, c.r + 40), Math.min(255, c.g + 40), Math.min(255, c.b + 40));
      int dark = PingMath.argb((int)(255 * alpha), c.r * 3 / 5, c.g * 3 / 5, c.b * 3 / 5);

      for (int dy = -r - 1; dy <= r + 1; dy++) {
         int half = r + 1 - Math.abs(dy);
         g.fill(-half, dy, half + 1, dy + 1, rim);
      }

      for (int dy = -r; dy <= r; dy++) {
         int half = r - Math.abs(dy);
         g.fill(-half, dy, 1, dy + 1, dy <= 0 ? lit : dark);
         g.fill(1, dy, half + 1, dy + 1, dy < 0 ? lit : dark);
      }
   }

   @Override
   public String getInfoString() {
      return this.pings.isEmpty() ? null : Integer.toString(this.pings.size());
   }

   private static final class Ping {
      Entity entity;
      double x;
      double y;
      double z;
      double rx;
      double ry;
      double rz;
      int bx;
      int by;
      int bz;
      long placedAt;
   }
}
