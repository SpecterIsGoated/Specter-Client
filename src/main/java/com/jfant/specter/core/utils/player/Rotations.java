package com.jfant.specter.core.utils.player;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.SendMovementPacketsEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.PreInit;
import com.jfant.specter.core.utils.entity.Target;
import com.jfant.specter.core.utils.misc.Pool;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Rotations {
   private static final Pool<Rotations.Rotation> rotationPool = new Pool<>(Rotations.Rotation::new);
   private static final List<Rotations.Rotation> rotations = new ArrayList<>();
   public static float serverYaw;
   public static float serverPitch;
   public static int rotationTimer;
   private static float preYaw;
   private static float prePitch;
   private static int i = 0;
   private static Rotations.Rotation lastRotation;
   private static int lastRotationTimer;
   private static boolean sentLastRotation;
   public static boolean rotating = false;

   private Rotations() {
   }

   @PreInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(Rotations.class);
   }

   public static void rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
      Rotations.Rotation rotation = rotationPool.get();
      rotation.set(yaw, pitch, priority, clientSide, callback);
      int i = 0;

      while (i < rotations.size() && priority <= rotations.get(i).priority) {
         i++;
      }

      rotations.add(i, rotation);
   }

   public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
      rotate(yaw, pitch, priority, false, callback);
   }

   public static void rotate(double yaw, double pitch, Runnable callback) {
      rotate(yaw, pitch, 0, callback);
   }

   public static void rotate(double yaw, double pitch, int priority) {
      rotate(yaw, pitch, priority, null);
   }

   public static void rotate(double yaw, double pitch) {
      rotate(yaw, pitch, 0, null);
   }

   private static void resetLastRotation() {
      if (lastRotation != null) {
         rotationPool.free(lastRotation);
         lastRotation = null;
         lastRotationTimer = 0;
      }
   }

   @EventHandler
   private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
      if (SpecterCore.mc.getCameraEntity() == SpecterCore.mc.player) {
         sentLastRotation = false;
         if (!rotations.isEmpty()) {
            rotating = true;
            resetLastRotation();
            Rotations.Rotation rotation = rotations.get(i);
            setupMovementPacketRotation(rotation);
            if (rotations.size() > 1) {
               rotationPool.free(rotation);
            }

            i++;
         } else if (lastRotation != null) {
            if (lastRotationTimer >= Config.get().rotationHoldTicks.get()) {
               resetLastRotation();
               rotating = false;
            } else {
               setupMovementPacketRotation(lastRotation);
               sentLastRotation = true;
               lastRotationTimer++;
            }
         }
      }
   }

   private static void setupMovementPacketRotation(Rotations.Rotation rotation) {
      setClientRotation(rotation);
      setCamRotation(rotation.yaw, rotation.pitch);
   }

   private static void setClientRotation(Rotations.Rotation rotation) {
      preYaw = SpecterCore.mc.player.getYRot();
      prePitch = SpecterCore.mc.player.getXRot();
      SpecterCore.mc.player.setYRot((float)rotation.yaw);
      SpecterCore.mc.player.setXRot((float)rotation.pitch);
   }

   @EventHandler
   private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      if (!rotations.isEmpty()) {
         if (SpecterCore.mc.getCameraEntity() == SpecterCore.mc.player) {
            rotations.get(i - 1).runCallback();
            if (rotations.size() == 1) {
               lastRotation = rotations.get(i - 1);
            }

            resetPreRotation();
         }

         for (; i < rotations.size(); i++) {
            Rotations.Rotation rotation = rotations.get(i);
            setCamRotation(rotation.yaw, rotation.pitch);
            if (rotation.clientSide) {
               setClientRotation(rotation);
            }

            rotation.sendPacket();
            if (rotation.clientSide) {
               resetPreRotation();
            }

            if (i == rotations.size() - 1) {
               lastRotation = rotation;
            } else {
               rotationPool.free(rotation);
            }
         }

         rotations.clear();
         i = 0;
      } else if (sentLastRotation) {
         resetPreRotation();
      }
   }

   private static void resetPreRotation() {
      SpecterCore.mc.player.setYRot(preYaw);
      SpecterCore.mc.player.setXRot(prePitch);
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      rotationTimer++;
   }

   public static double getYaw(Entity entity) {
      return SpecterCore.mc.player.getYRot()
         + Mth.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(entity.getZ() - SpecterCore.mc.player.getZ(), entity.getX() - SpecterCore.mc.player.getX()))
               - 90.0F
               - SpecterCore.mc.player.getYRot()
         );
   }

   public static double getYaw(Vec3 pos) {
      return SpecterCore.mc.player.getYRot()
         + Mth.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(pos.z() - SpecterCore.mc.player.getZ(), pos.x() - SpecterCore.mc.player.getX()))
               - 90.0F
               - SpecterCore.mc.player.getYRot()
         );
   }

   public static double getPitch(Vec3 pos) {
      double diffX = pos.x() - SpecterCore.mc.player.getX();
      double diffY = pos.y() - (SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(SpecterCore.mc.player.getPose()));
      double diffZ = pos.z() - SpecterCore.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return SpecterCore.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - SpecterCore.mc.player.getXRot());
   }

   public static double getPitch(Entity entity, Target target) {
      double y;
      if (target == Target.Head) {
         y = entity.getEyeY();
      } else if (target == Target.Body) {
         y = entity.getY() + entity.getBbHeight() / 2.0F;
      } else {
         y = entity.getY();
      }

      double diffX = entity.getX() - SpecterCore.mc.player.getX();
      double diffY = y - (SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(SpecterCore.mc.player.getPose()));
      double diffZ = entity.getZ() - SpecterCore.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return SpecterCore.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - SpecterCore.mc.player.getXRot());
   }

   public static double getPitch(Entity entity) {
      return getPitch(entity, Target.Body);
   }

   public static double getYaw(BlockPos pos) {
      return SpecterCore.mc.player.getYRot()
         + Mth.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(pos.getZ() + 0.5 - SpecterCore.mc.player.getZ(), pos.getX() + 0.5 - SpecterCore.mc.player.getX()))
               - 90.0F
               - SpecterCore.mc.player.getYRot()
         );
   }

   public static double getPitch(BlockPos pos) {
      double diffX = pos.getX() + 0.5 - SpecterCore.mc.player.getX();
      double diffY = pos.getY() + 0.5 - (SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(SpecterCore.mc.player.getPose()));
      double diffZ = pos.getZ() + 0.5 - SpecterCore.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return SpecterCore.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - SpecterCore.mc.player.getXRot());
   }

   public static void setCamRotation(double yaw, double pitch) {
      serverYaw = (float)yaw;
      serverPitch = (float)pitch;
      rotationTimer = 0;
   }

   private static class Rotation {
      public double yaw;
      public double pitch;
      public int priority;
      public boolean clientSide;
      public Runnable callback;

      public void set(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
         this.yaw = yaw;
         this.pitch = pitch;
         this.priority = priority;
         this.clientSide = clientSide;
         this.callback = callback;
      }

      public void sendPacket() {
         SpecterCore.mc
            .getConnection()
            .send(new Rot((float)this.yaw, (float)this.pitch, SpecterCore.mc.player.onGround(), SpecterCore.mc.player.horizontalCollision));
         this.runCallback();
      }

      public void runCallback() {
         if (this.callback != null) {
            this.callback.run();
         }
      }
   }
}
