package com.jfant.specter.build;

import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class HumanAim {
   private static final double FALLBACK_PEAK = 8.0;
   private static final double FALLBACK_OVERSHOOT_RATE = 0.25;
   private static final double OVERSHOOT_MIN_TURN = 5.0;
   private static final double OVERSHOOT_CAP = 6.0;
   private static final double UNDERSHOOT_MIN_TURN = 8.0;
   private static final double UNDERSHOOT_RATE = 0.35;
   private static final double MIN_DURATION = 90.0;
   private static final double DURATION_PER_BIT = 15.0;
   private static final double MAX_DURATION = 900.0;
   private static final double CORRECTION_DELAY_MIN = 40.0;
   private static final double CORRECTION_DELAY_MAX = 130.0;
   private static final double HURRY_FLOOR = 1.3;
   private static final double FOLLOW_DEGREES = 0.5;
   private static final double EXTERNAL_TURN = 20.0;
   private final com.jfant.specter.build.BuildProfile.Aim profile;
   private final Random random;
   private double speed = 1.0;
   private boolean active;
   private double goalYaw;
   private double goalPitch;
   private com.jfant.specter.build.HumanAim.Move move;
   private final ArrayDeque<com.jfant.specter.build.HumanAim.Pending> queue = new ArrayDeque<>();
   private double queueReadyMs;
   private double lastYaw = Double.NaN;
   private double lastPitch = Double.NaN;

   public HumanAim(com.jfant.specter.build.BuildProfile.Aim profile, Random random) {
      this.profile = profile;
      this.random = random;
   }

   public void setSpeed(double speed) {
      this.speed = Math.max(0.1, speed);
   }

   public void target(LocalPlayer player, Vec3 eye, Vec3 point) {
      double[] rot = rotationTo(eye, point);
      double yaw = player.getYRot() + Mth.wrapDegrees(rot[0] - player.getYRot());
      double pitch = rot[1];
      if (!this.active || !(Math.hypot(yaw - this.goalYaw, pitch - this.goalPitch) < 1.0)) {
         this.goalYaw = yaw;
         this.goalPitch = pitch;
         this.active = true;
         this.queue.clear();
         double now = nowMs();
         double[] velocity = this.velocityAt(now);
         double fromYaw = player.getYRot();
         double fromPitch = player.getXRot();
         double distance = Math.hypot(yaw - fromYaw, pitch - fromPitch);
         if (distance > 5.0 && this.random.nextDouble() < this.overshootRate()) {
            double past = Math.min(6.0, this.sample(this.profile.overshootDegrees, 0.0, 100.0, 1.5));
            double overYaw = yaw + (yaw - fromYaw) / distance * past;
            double overPitch = Mth.clamp(pitch + (pitch - fromPitch) / distance * past, -90.0, 90.0);
            this.startMove(now, fromYaw, fromPitch, velocity, overYaw, overPitch);
            this.queue.add(new com.jfant.specter.build.HumanAim.Pending(yaw, pitch, this.correctionDelay()));
         } else if (distance > 8.0 && this.random.nextDouble() < 0.35) {
            double reach = 0.9 + this.random.nextDouble() * 0.08;
            this.startMove(now, fromYaw, fromPitch, velocity, fromYaw + (yaw - fromYaw) * reach, fromPitch + (pitch - fromPitch) * reach);
            this.queue.add(new com.jfant.specter.build.HumanAim.Pending(yaw, pitch, this.correctionDelay()));
         } else {
            this.startMove(now, fromYaw, fromPitch, velocity, yaw, pitch);
         }

         this.lastYaw = fromYaw;
         this.lastPitch = fromPitch;
      }
   }

   public void follow(LocalPlayer player, Vec3 eye, Vec3 point) {
      if (!this.active) {
         this.target(player, eye, point);
      } else {
         double[] rot = rotationTo(eye, point);
         double yaw = player.getYRot() + Mth.wrapDegrees(rot[0] - player.getYRot());
         double pitch = rot[1];
         if (!(Math.hypot(yaw - this.goalYaw, pitch - this.goalPitch) < 0.5)) {
            this.goalYaw = yaw;
            this.goalPitch = pitch;
            if (!this.queue.isEmpty()) {
               com.jfant.specter.build.HumanAim.Pending next = this.queue.poll();
               this.queue.clear();
               this.queue.add(new com.jfant.specter.build.HumanAim.Pending(yaw, pitch, next.delayMs()));
            } else {
               double now = nowMs();
               this.startMove(now, player.getYRot(), player.getXRot(), this.velocityAt(now), yaw, pitch);
               this.lastYaw = player.getYRot();
               this.lastPitch = player.getXRot();
            }
         }
      }
   }

   public void stop() {
      this.active = false;
      this.move = null;
      this.queue.clear();
      this.lastYaw = Double.NaN;
   }

   public boolean arrived(Minecraft mc) {
      return this.active && mc.player != null ? this.move == null && this.queue.isEmpty() && this.remaining(mc) < degreesPerCount(mc) * 1.5 : true;
   }

   public double remaining(Minecraft mc) {
      return this.active && mc.player != null ? Math.hypot(this.goalYaw - mc.player.getYRot(), this.goalPitch - mc.player.getXRot()) : 0.0;
   }

   public void tick(Minecraft mc) {
      this.frame(mc);
   }

   public void idle(Minecraft mc) {
      this.frame(mc);
      if (mc.player != null && this.move == null && this.queue.isEmpty() && !(this.random.nextDouble() > 0.03)) {
         double yaw = mc.player.getYRot() + this.random.nextGaussian() * 0.25;
         double pitch = Mth.clamp(mc.player.getXRot() + this.random.nextGaussian() * 0.15, -90.0, 90.0);
         this.startMove(nowMs(), mc.player.getYRot(), mc.player.getXRot(), new double[]{0.0, 0.0}, yaw, pitch);
         this.lastYaw = mc.player.getYRot();
         this.lastPitch = mc.player.getXRot();
      }
   }

   public void frame(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (player != null && (this.move != null || !this.queue.isEmpty())) {
         if (Double.isNaN(this.lastYaw) || !(Math.abs(player.getYRot() - this.lastYaw) > 20.0) && !(Math.abs(player.getXRot() - this.lastPitch) > 20.0)) {
            double now = nowMs();
            if (this.move == null) {
               if (now < this.queueReadyMs) {
                  return;
               }

               com.jfant.specter.build.HumanAim.Pending next = this.queue.poll();
               this.startMove(now, player.getYRot(), player.getXRot(), new double[]{0.0, 0.0}, next.yaw(), next.pitch());
            }

            double[] want = this.positionAt(now);
            double perCount = degreesPerCount(mc);
            long countsX = (long)((want[0] - player.getYRot()) / perCount);
            long countsY = (long)((want[1] - player.getXRot()) / perCount);
            if (countsX != 0L || countsY != 0L) {
               double scale = sensitivityScale(mc);
               player.turn(countsX * scale, countsY * scale);
            }

            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            if (now >= this.move.startMs() + this.move.duration()) {
               this.move = null;
               if (!this.queue.isEmpty()) {
                  this.queueReadyMs = now + this.queue.peek().delayMs();
               }
            }
         } else {
            this.stop();
         }
      } else {
         this.lastYaw = Double.NaN;
      }
   }

   private void startMove(double now, double fromYaw, double fromPitch, double[] velocity, double toYaw, double toPitch) {
      double dYaw = toYaw - fromYaw;
      double dPitch = toPitch - fromPitch;
      double distance = Math.hypot(dYaw, dPitch);
      double duration = this.duration(distance);
      double dirYaw = distance < 1.0E-6 ? 0.0 : dYaw / distance;
      double dirPitch = distance < 1.0E-6 ? 0.0 : dPitch / distance;
      double bow = this.random.nextGaussian() * 0.04 * distance;
      this.move = new com.jfant.specter.build.HumanAim.Move(
         now,
         duration,
         fromYaw,
         fromPitch,
         velocity[0],
         velocity[1],
         coefficients(dYaw, velocity[0], duration),
         coefficients(dPitch, velocity[1], duration),
         dirYaw,
         dirPitch,
         bow
      );
   }

   private double duration(double distance) {
      if (distance < 1.0E-6) {
         return 90.0;
      } else {
         double bits = Math.log(1.0 + distance) / Math.log(2.0);
         double hurry = this.speed > 1.0 ? 10.0 * Math.log(this.speed) / Math.log(2.0) : 0.0;
         double percentile = Mth.clamp(33.0 + 10.0 * bits + hurry + this.random.nextGaussian() * 8.0, 20.0, 99.0);
         double peakPerTick = this.sample(this.profile.turnSpeed, percentile, percentile, 8.0);
         double peakPerMs = Math.max(0.01, peakPerTick / 50.0);
         double floor = (90.0 + 15.0 * bits) / Math.min(1.3, Math.max(1.0, this.speed));
         double duration = Math.max(floor, 1.875 * distance / peakPerMs);
         return Math.min(900.0, duration) / Math.min(1.0, this.speed);
      }
   }

   private static double[] coefficients(double h, double v0, double T) {
      return new double[]{
         (10.0 * h - 6.0 * v0 * T) / (T * T * T), (-15.0 * h + 8.0 * v0 * T) / (T * T * T * T), (6.0 * h - 3.0 * v0 * T) / (T * T * T * T * T)
      };
   }

   private double[] positionAt(double now) {
      double t = Mth.clamp(now - this.move.startMs(), 0.0, this.move.duration());
      double yaw = this.move.fromYaw() + along(this.move.vYaw(), this.move.cYaw(), t);
      double pitch = this.move.fromPitch() + along(this.move.vPitch(), this.move.cPitch(), t);
      double bow = this.move.bow() * Math.sin(Math.PI * t / this.move.duration());
      return new double[]{yaw - this.move.dirPitch() * bow, Mth.clamp(pitch + this.move.dirYaw() * bow, -90.0, 90.0)};
   }

   private double[] velocityAt(double now) {
      if (this.move == null) {
         return new double[]{0.0, 0.0};
      } else {
         double t = now - this.move.startMs();
         return !(t < 0.0) && !(t >= this.move.duration())
            ? new double[]{speedAlong(this.move.vYaw(), this.move.cYaw(), t), speedAlong(this.move.vPitch(), this.move.cPitch(), t)}
            : new double[]{0.0, 0.0};
      }
   }

   private static double along(double v0, double[] c, double t) {
      return v0 * t + c[0] * t * t * t + c[1] * t * t * t * t + c[2] * t * t * t * t * t;
   }

   private static double speedAlong(double v0, double[] c, double t) {
      return v0 + 3.0 * c[0] * t * t + 4.0 * c[1] * t * t * t + 5.0 * c[2] * t * t * t * t;
   }

   public static double[] rotationTo(Vec3 eye, Vec3 point) {
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
      double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
      return new double[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0, 90.0)};
   }

   private double overshootRate() {
      return this.profile.overshootDegrees != null && !this.profile.overshootDegrees.empty() ? this.profile.overshootRate : 0.25;
   }

   private double correctionDelay() {
      return (40.0 + this.random.nextDouble() * 90.0) / Math.min(1.3, this.speed);
   }

   private static double nowMs() {
      return System.nanoTime() / 1000000.0;
   }

   public static double sensitivityScale(Minecraft mc) {
      double d = (Double)mc.options.sensitivity().get() * 0.6 + 0.2;
      return d * d * d * 8.0;
   }

   public static double degreesPerCount(Minecraft mc) {
      return sensitivityScale(mc) * 0.15;
   }

   private double sample(com.jfant.specter.build.BuildProfile.Dist dist, double fromPercentile, double toPercentile, double fallback) {
      return dist != null && !dist.empty() ? dist.p(fromPercentile + this.random.nextDouble() * (toPercentile - fromPercentile)) : fallback;
   }

   private record Move(
      double startMs,
      double duration,
      double fromYaw,
      double fromPitch,
      double vYaw,
      double vPitch,
      double[] cYaw,
      double[] cPitch,
      double dirYaw,
      double dirPitch,
      double bow
   ) {
   }

   private record Pending(double yaw, double pitch, double delayMs) {
   }
}
