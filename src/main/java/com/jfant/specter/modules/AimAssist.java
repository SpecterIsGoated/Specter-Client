package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.build.HumanAim;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.pvp.AimMath;
import com.jfant.specter.pvp.Melee;
import com.jfant.specter.pvp.MouseTurns;
import com.jfant.specter.pvp.Targets;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class AimAssist extends Module {
   private static final long HAND_WINDOW_MS = 180L;
   private static final double CORE_INSET = 0.1;
   private static final double PITCH_WEIGHT = 0.6;
   private static final double STICKY_MARGIN = 0.45;
   private static final double STICKY_CAP = 540.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("How far a target can be.").defaultValue(4.5).range(3.0, 8.0).sliderRange(3.0, 8.0).build());
   private final Setting<Double> fov = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fov")
            .description("Only targets within this many degrees of the crosshair (the whole cone, so 60 = 30 either side).")
            .defaultValue(70.0)
            .range(10.0, 180.0)
            .sliderRange(10.0, 180.0)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("The most the pull adds to your turn, in degrees a second.")
            .defaultValue(110.0)
            .range(10.0, 360.0)
            .sliderRange(10.0, 360.0)
            .build()
      );
   private final Setting<Integer> sticky = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sticky")
            .description("How much of a target's movement the crosshair follows while it is on them, in percent. 0 = off.")
            .defaultValue(65)
            .range(0, 100)
            .sliderRange(0, 100)
            .build()
      );
   private final Setting<Boolean> pullWhenStill = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pull-when-still")
            .description("Also pull onto the target while your mouse is still (much more noticeable). Off: only helps turns you make.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> vertical = this.sgGeneral
      .add(new BoolSetting.Builder().name("vertical").description("Also help up and down (more gently than side to side).").defaultValue(true).build());
   private final Setting<Boolean> weaponsOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("weapons-only").description("Only with a sword, axe, mace or trident in hand.").defaultValue(true).build());
   private final Setting<Boolean> players = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Help onto players (never ones on your friends list).").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets
      .add(new BoolSetting.Builder().name("hostiles").description("Help onto hostile mobs.").defaultValue(false).build());
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Say in chat (only you see it) when the target changes, and the pace drawn for it.")
            .defaultValue(false)
            .build()
      );
   @Nullable
   private LivingEntity target;
   private double pace = 1.0;
   private double carryYaw;
   private double carryPitch;
   private long lastFrame;
   private double lastCenterYaw = 0.0 / 0.0;
   private double lastCenterPitch = 0.0 / 0.0;

   public AimAssist() {
      super(
         SpecterClientAddon.PVP,
         "aim-assist",
         "Helps your aim: pulls your turns onto the nearest target and keeps the crosshair stuck to them as they strafe, in real mouse steps."
      );
   }

   @Override
   public void onActivate() {
      this.target = null;
      this.carryYaw = this.carryPitch = 0.0;
      this.lastFrame = 0L;
      this.lastCenterYaw = this.lastCenterPitch = 0.0 / 0.0;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      long now = System.nanoTime();
      double seconds = this.lastFrame == 0L ? 0.0 : Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      LocalPlayer player = this.mc.player;
      if (player != null
         && this.mc.level != null
         && this.mc.gui.screen() == null
         && GhostFreecam.on() == null
         && GhostFreelook.on() == null
         && !player.isUsingItem()
         && (!this.weaponsOnly.get() || Melee.isWeapon(player.getMainHandItem()))) {
         float delta = event.tickDelta;
         Vec3 eye = player.getEyePosition(delta);
         LivingEntity chosen = this.choose(player, eye, delta);
         if (chosen != this.target) {
            this.target = chosen;
            this.pace = 0.85 + ThreadLocalRandom.current().nextDouble() * 0.3;
            this.carryYaw = this.carryPitch = 0.0;
            this.lastCenterYaw = 0.0 / 0.0;
            if (this.debug.get() && chosen != null) {
               this.info("Target %s, pace %.2f.", new Object[]{chosen.getName().getString(), this.pace});
            }
         }

         if (this.target != null) {
            AABB box = lerpedBox(this.target, delta);
            Vec3 look = player.getViewVector(delta);
            double[] center = HumanAim.rotationTo(eye, box.getCenter());
            double followYaw = 0.0;
            double followPitch = 0.0;
            if (this.sticky.get() > 0
               && !Double.isNaN(this.lastCenterYaw)
               && seconds > 0.0
               && box.inflate(0.45).clip(eye, eye.add(look.scale(this.range.get() + 2.0))).isPresent()) {
               double share = this.sticky.get().intValue() / 100.0;
               double cap = 540.0 * seconds;
               followYaw = Mth.clamp(AimMath.wrap(this.lastCenterYaw, center[0]) * share, -cap, cap);
               followPitch = this.vertical.get() ? Mth.clamp((center[1] - this.lastCenterPitch) * share, -cap, cap) : 0.0;
            }

            this.lastCenterYaw = center[0];
            this.lastCenterPitch = center[1];
            double dYaw = 0.0;
            double dPitch = 0.0;
            boolean handMoving = MouseTurns.within(180L);
            if (handMoving || this.pullWhenStill.get()) {
               Vec3 point = aimPoint(eye, look, box);
               if (point != null) {
                  double[] want = HumanAim.rotationTo(eye, point);
                  double cap = this.speed.get() * this.pace * (handMoving ? 1.0 : 0.5);
                  dYaw = AimMath.step(AimMath.wrap(player.getYRot(), want[0]), cap, seconds);
                  dPitch = this.vertical.get() ? AimMath.step(want[1] - player.getXRot(), cap * 0.6, seconds) : 0.0;
                  if (handMoving) {
                     if (!AimMath.withHand(dYaw, MouseTurns.dx())) {
                        dYaw = 0.0;
                     }

                     if (!AimMath.withHand(dPitch, MouseTurns.dy())) {
                        dPitch = 0.0;
                     }
                  }
               }
            }

            double perCount = HumanAim.degreesPerCount(this.mc);
            double[] yawCounts = AimMath.counts(dYaw + followYaw, this.carryYaw, perCount);
            double[] pitchCounts = AimMath.counts(dPitch + followPitch, this.carryPitch, perCount);
            this.carryYaw = yawCounts[1];
            this.carryPitch = pitchCounts[1];
            if (yawCounts[0] != 0.0 || pitchCounts[0] != 0.0) {
               double scale = HumanAim.sensitivityScale(this.mc);
               player.turn(yawCounts[0] * scale, pitchCounts[0] * scale);
            }
         }
      } else {
         this.lastCenterYaw = 0.0 / 0.0;
      }
   }

   @Nullable
   private LivingEntity choose(LocalPlayer player, Vec3 eye, float delta) {
      double half = this.fov.get() / 2.0;
      if (this.target != null && this.valid(player, this.target) && angleTo(player, eye, this.target, delta) <= half * 1.3) {
         return this.target;
      } else {
         LivingEntity best = null;
         double bestAngle = half;

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && this.valid(player, living)) {
               double angle = angleTo(player, eye, living, delta);
               if (angle <= bestAngle) {
                  best = living;
                  bestAngle = angle;
               }
            }
         }

         return best;
      }
   }

   private boolean valid(LocalPlayer player, LivingEntity entity) {
      return entity != player
         && entity.isAlive()
         && Targets.fightable(entity, this.players.get(), this.hostiles.get())
         && player.getEyePosition().distanceTo(entity.getBoundingBox().getCenter()) <= this.range.get() + 1.0
         && player.hasLineOfSight(entity);
   }

   private static double angleTo(LocalPlayer player, Vec3 eye, LivingEntity entity, float delta) {
      double[] want = HumanAim.rotationTo(eye, lerpedBox(entity, delta).getCenter());
      return Math.hypot(AimMath.wrap(player.getYRot(), want[0]), want[1] - player.getXRot());
   }

   static AABB lerpedBox(Entity entity, float delta) {
      Vec3 drawn = entity.getPosition(delta);
      Vec3 pos = entity.position();
      return entity.getBoundingBox().move(drawn.x - pos.x, drawn.y - pos.y, drawn.z - pos.z);
   }

   @Nullable
   private static Vec3 aimPoint(Vec3 eye, Vec3 look, AABB box) {
      if (box.clip(eye, eye.add(look.scale(8.0))).isPresent()) {
         return null;
      } else {
         AABB core = box.deflate(0.1, 0.1, 0.1);
         double along = Math.max(0.0, core.getCenter().subtract(eye).dot(look));
         Vec3 onLine = eye.add(look.scale(along));
         return new Vec3(Mth.clamp(onLine.x, core.minX, core.maxX), Mth.clamp(onLine.y, core.minY, core.maxY), Mth.clamp(onLine.z, core.minZ, core.maxZ));
      }
   }
}
