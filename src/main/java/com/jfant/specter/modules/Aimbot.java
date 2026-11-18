package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.build.BuildProfile;
import com.jfant.specter.build.HumanAim;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.pvp.AimMath;
import com.jfant.specter.pvp.Melee;
import com.jfant.specter.pvp.Targets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class Aimbot extends Module {
   private static final double TRACK_ENTER = 2.5;
   private static final double TRACK_EXIT = 9.0;
   private static final long SIGHT_GRACE_MS = 600L;
   private static final double TRACK_CAP = 720.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAim = this.settings.createGroup("Aim");
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final Setting<Aimbot.Activation> activation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("activation"))
                  .description("Always: aims whenever the module is on. Hold Attack: while you hold left-click. Hold Key: while you hold the key below."))
               .defaultValue(Aimbot.Activation.Always))
            .build()
      );
   private final Setting<Keybind> key = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("hold-key")
            .description("The key to hold, with activation on Hold Key.")
            .defaultValue(Keybind.none())
            .visible(() -> this.activation.get() == Aimbot.Activation.HoldKey)
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("How far a target can be.").defaultValue(5.0).range(3.0, 10.0).sliderRange(3.0, 10.0).build());
   private final Setting<Double> fov = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fov")
            .description(
               "How far from the crosshair a new target can be picked, the whole cone in degrees (360 = anywhere). A locked target is kept wherever it goes."
            )
            .defaultValue(120.0)
            .range(10.0, 360.0)
            .sliderRange(10.0, 360.0)
            .build()
      );
   private final Setting<Boolean> weaponsOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("weapons-only").description("Only with a sword, axe, mace or trident in hand.").defaultValue(false).build());
   private final Setting<Boolean> human = this.sgAim
      .add(
         new BoolSetting.Builder()
            .name("human-aim")
            .description("Turn onto targets like a hand on a mouse (curves, reaction time, slight overshoots) and track smoothly. Off: straight, hard lock.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> speed = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("How fast it turns onto a target and tracks it. 1 = a quick player, 2 = very fast, 3+ = inhuman.")
            .defaultValue(1.3)
            .range(0.3, 4.0)
            .sliderRange(0.3, 4.0)
            .build()
      );
   private final Setting<Integer> reaction = this.sgAim
      .add(
         new IntSetting.Builder()
            .name("reaction-ms")
            .description("Milliseconds before turning onto a new target (a little different every time).")
            .defaultValue(140)
            .range(0, 500)
            .sliderRange(0, 400)
            .visible(this.human::get)
            .build()
      );
   private final Setting<Double> maxSpeed = this.sgAim
      .add(
         new DoubleSetting.Builder()
            .name("max-speed")
            .description("Without human aim: the fastest it turns, degrees a second.")
            .defaultValue(400.0)
            .range(30.0, 1800.0)
            .sliderRange(30.0, 1800.0)
            .visible(() -> !this.human.get())
            .build()
      );
   private final Setting<Aimbot.AimPoint> aimPoint = this.sgAim
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("aim-point"))
                  .description("Where on the body: Chest, Head, or Nearest (the closest point of the body to your crosshair - the least movement)."))
               .defaultValue(Aimbot.AimPoint.Chest))
            .build()
      );
   private final Setting<Boolean> vertical = this.sgAim
      .add(
         new BoolSetting.Builder()
            .name("vertical")
            .description("Also aim up and down. Off: only side to side, you handle the height.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> players = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Lock onto players (never ones on your friends list).").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets
      .add(new BoolSetting.Builder().name("hostiles").description("Lock onto hostile mobs.").defaultValue(false).build());
   private final Random random = new Random();
   private HumanAim hand;
   private double handSpeed = -1.0;
   @Nullable
   private LivingEntity target;
   private boolean tracking;
   private long acquiredAt;
   private long startAt;
   private long lastSeen;
   private long lastFrame;
   private double lastCenterYaw = 0.0 / 0.0;
   private double lastCenterPitch;
   private double carryYaw;
   private double carryPitch;
   private double offX = 0.5;
   private double offY = 0.5;
   private double offZ = 0.5;
   private double goalX = 0.5;
   private double goalY = 0.5;
   private double goalZ = 0.5;
   private long nextGoalAt;

   public Aimbot() {
      super(
         SpecterClientAddon.BLATANT,
         "aimbot",
         "Locks your aim onto the nearest target and keeps it there, with human-looking turns and tracking (or a hard lock with human-aim off)."
      );
   }

   @Override
   public void onActivate() {
      this.release();
   }

   @Override
   public void onDeactivate() {
      this.release();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.release();
   }

   private void release() {
      this.target = null;
      this.tracking = false;
      this.lastFrame = 0L;
      this.lastCenterYaw = 0.0 / 0.0;
      this.carryYaw = this.carryPitch = 0.0;
      if (this.hand != null) {
         this.hand.stop();
      }
   }

   private boolean engaged() {
      return switch ((Aimbot.Activation)this.activation.get()) {
         case Always -> true;
         case HoldAttack -> Input.isPressed(this.mc.options.keyAttack);
         case HoldKey -> this.key.get().isSet() && this.key.get().isPressed();
      };
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      long now = System.nanoTime();
      double seconds = this.lastFrame == 0L ? 0.0 : Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      LocalPlayer player = this.mc.player;
      if (player != null
         && this.mc.level != null
         && this.mc.screen == null
         && GhostFreecam.on() == null
         && GhostFreelook.on() == null
         && this.engaged()
         && (!this.weaponsOnly.get() || Melee.isWeapon(player.getMainHandItem()))) {
         float delta = event.tickDelta;
         Vec3 eye = player.getEyePosition(delta);
         LivingEntity chosen = this.choose(player, eye, delta, now);
         if (chosen != this.target) {
            this.release();
            this.target = chosen;
            this.lastFrame = now;
            if (chosen == null) {
               return;
            }

            this.acquiredAt = now;
            double react = this.human.get() ? this.reaction.get().intValue() * (0.8 + this.random.nextDouble() * 0.4) : 0.0;
            this.startAt = now + (long)(react * 1000000.0);
            this.offX = this.goalX = 0.5;
            this.offY = this.goalY = this.aimHeight();
            this.offZ = this.goalZ = 0.5;
            this.nextGoalAt = now;
         }

         if (this.target != null && now >= this.startAt) {
            AABB box = AimAssist.lerpedBox(this.target, delta);
            Vec3 look = player.getViewVector(delta);
            Vec3 point = this.point(box, eye, look, now, seconds);
            double[] want = HumanAim.rotationTo(eye, point);
            double[] center = HumanAim.rotationTo(eye, box.getCenter());
            double errYaw = AimMath.wrap(player.getYRot(), want[0]);
            double errPitch = this.vertical.get() ? want[1] - player.getXRot() : 0.0;
            double error = Math.hypot(errYaw, errPitch);
            if (!this.human.get()) {
               double cap = this.maxSpeed.get() * seconds;
               this.turn(Mth.clamp(errYaw, -cap, cap), Mth.clamp(errPitch, -cap, cap));
               this.lastCenterYaw = center[0];
               this.lastCenterPitch = center[1];
            } else {
               if (this.tracking && error > 9.0) {
                  this.tracking = false;
                  this.hand().stop();
               } else if (!this.tracking && error < 2.5) {
                  this.tracking = true;
                  this.hand().stop();
                  this.lastCenterYaw = 0.0 / 0.0;
                  this.carryYaw = this.carryPitch = 0.0;
               }

               if (!this.tracking) {
                  HumanAim aim = this.hand();
                  Vec3 aimAt = this.vertical.get()
                     ? point
                     : new Vec3(point.x, eye.y + Math.tan(Math.toRadians(-player.getXRot())) * Math.hypot(point.x - eye.x, point.z - eye.z), point.z);
                  aim.follow(player, eye, aimAt);
                  aim.frame(this.mc);
               } else {
                  double followYaw = 0.0;
                  double followPitch = 0.0;
                  if (!Double.isNaN(this.lastCenterYaw) && seconds > 0.0) {
                     double cap = 720.0 * seconds;
                     followYaw = Mth.clamp(AimMath.wrap(this.lastCenterYaw, center[0]), -cap, cap);
                     followPitch = this.vertical.get() ? Mth.clamp(center[1] - this.lastCenterPitch, -cap, cap) : 0.0;
                  }

                  this.lastCenterYaw = center[0];
                  this.lastCenterPitch = center[1];
                  double rate = 7.0 * this.speed.get();
                  double close = 1.0 - Math.exp(-rate * seconds);
                  double cap = 90.0 * this.speed.get() * seconds;
                  this.turn(followYaw + Mth.clamp(errYaw * close, -cap, cap), followPitch + Mth.clamp(errPitch * close, -cap, cap));
               }
            }
         }
      } else {
         if (this.target != null) {
            this.release();
         }
      }
   }

   private void turn(double yaw, double pitch) {
      double perCount = HumanAim.degreesPerCount(this.mc);
      double[] yawCounts = AimMath.counts(yaw, this.carryYaw, perCount);
      double[] pitchCounts = AimMath.counts(pitch, this.carryPitch, perCount);
      this.carryYaw = yawCounts[1];
      this.carryPitch = pitchCounts[1];
      if (yawCounts[0] != 0.0 || pitchCounts[0] != 0.0) {
         double scale = HumanAim.sensitivityScale(this.mc);
         this.mc.player.turn(yawCounts[0] * scale, pitchCounts[0] * scale);
      }
   }

   private HumanAim hand() {
      if (this.hand == null || this.handSpeed != this.speed.get()) {
         this.handSpeed = this.speed.get();
         BuildProfile.Aim profile = new BuildProfile.Aim();
         List<Double> speeds = new ArrayList<>();

         for (int i = 0; i <= 100; i++) {
            speeds.add(4.0 * Math.exp(i / 100.0 * Math.log(12.0)) * this.handSpeed);
         }

         profile.turnSpeed = BuildProfile.Dist.of(speeds);
         List<Double> overshoot = new ArrayList<>();

         for (int i = 0; i <= 20; i++) {
            overshoot.add(0.6 + i * 0.12);
         }

         profile.overshootDegrees = BuildProfile.Dist.of(overshoot);
         profile.overshootRate = 0.2;
         if (this.hand != null) {
            this.hand.stop();
         }

         this.hand = new HumanAim(profile, this.random);
      }

      return this.hand;
   }

   private double aimHeight() {
      return this.aimPoint.get() == Aimbot.AimPoint.Head ? 0.88 : 0.62;
   }

   private Vec3 point(AABB box, Vec3 eye, Vec3 look, long now, double seconds) {
      AABB core = box.deflate(0.08, 0.05, 0.08);
      if (this.aimPoint.get() == Aimbot.AimPoint.Nearest) {
         double along = Math.max(0.0, core.getCenter().subtract(eye).dot(look));
         Vec3 onLine = eye.add(look.scale(along));
         return new Vec3(Mth.clamp(onLine.x, core.minX, core.maxX), Mth.clamp(onLine.y, core.minY, core.maxY), Mth.clamp(onLine.z, core.minZ, core.maxZ));
      } else {
         if (now >= this.nextGoalAt) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double height = this.aimHeight();
            this.goalX = 0.5 + (r.nextDouble() - 0.5) * 0.5;
            this.goalZ = 0.5 + (r.nextDouble() - 0.5) * 0.5;
            this.goalY = Mth.clamp(height + (r.nextDouble() - 0.5) * 0.16, 0.1, 0.95);
            this.nextGoalAt = now + (long)((350.0 + r.nextDouble() * 550.0) * 1000000.0);
         }

         double ease = 1.0 - Math.exp(-4.0 * seconds);
         this.offX = this.offX + (this.goalX - this.offX) * ease;
         this.offY = this.offY + (this.goalY - this.offY) * ease;
         this.offZ = this.offZ + (this.goalZ - this.offZ) * ease;
         return new Vec3(Mth.lerp(this.offX, core.minX, core.maxX), Mth.lerp(this.offY, core.minY, core.maxY), Mth.lerp(this.offZ, core.minZ, core.maxZ));
      }
   }

   @Nullable
   private LivingEntity choose(LocalPlayer player, Vec3 eye, float delta, long now) {
      if (this.target != null
         && this.target.isAlive()
         && !this.target.isRemoved()
         && Targets.fightable(this.target, this.players.get(), this.hostiles.get())
         && eye.distanceTo(this.target.getBoundingBox().getCenter()) <= this.range.get() + 1.0) {
         if (player.hasLineOfSight(this.target)) {
            this.lastSeen = now;
         }

         if (now - this.lastSeen <= 600000000L) {
            return this.target;
         }
      }

      double half = this.fov.get() / 2.0;
      LivingEntity best = null;
      double bestAngle = 1.7976931348623157E308;

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living
            && living != player
            && living.isAlive()
            && Targets.fightable(living, this.players.get(), this.hostiles.get())
            && !(eye.distanceTo(living.getBoundingBox().getCenter()) > this.range.get() + 1.0)
            && player.hasLineOfSight(living)) {
            double[] want = HumanAim.rotationTo(eye, AimAssist.lerpedBox(living, delta).getCenter());
            double angle = Math.hypot(AimMath.wrap(player.getYRot(), want[0]), want[1] - player.getXRot());
            if (angle <= half && angle < bestAngle) {
               best = living;
               bestAngle = angle;
            }
         }
      }

      if (best != null) {
         this.lastSeen = now;
      }

      return best;
   }

   public static enum Activation {
      Always("Always"),
      HoldAttack("Hold Attack"),
      HoldKey("Hold Key");

      private final String title;

      private Activation(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum AimPoint {
      Chest("Chest"),
      Head("Head"),
      Nearest("Nearest");

      private final String title;

      private AimPoint(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
