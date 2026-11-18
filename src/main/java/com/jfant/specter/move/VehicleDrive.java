package com.jfant.specter.move;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class VehicleDrive {
   double horizontalSpeed = 12.0;
   double verticalSpeed = 8.0;
   double idleFall = 0.0;
   double acceleration = 0.4;
   boolean lockYaw = true;
   boolean up;
   boolean down;
   private double curX;
   private double curY;
   private double curZ;
   private double preX;
   private double preY;
   private double preZ;
   private boolean moved;
   private final double[] dir = new double[2];

   void reset() {
      this.curX = this.curY = this.curZ = 0.0;
      this.moved = false;
   }

   Vec3 step(Entity vehicle, LocalPlayer player) {
      boolean asked;
      if (this.lockYaw) {
         vehicle.setYRot(player.getYRot());
         asked = MoveInput.direction(player.getYRot(), this.dir);
      } else {
         double axis = (MoveInput.forward() ? 1 : 0) - (MoveInput.back() ? 1 : 0);
         double yaw = Math.toRadians(vehicle.getYRot());
         this.dir[0] = -Math.sin(yaw) * axis;
         this.dir[1] = Math.cos(yaw) * axis;
         asked = axis != 0.0;
      }

      double perTick = this.horizontalSpeed / 20.0;
      double targetY = this.up == this.down ? -this.idleFall / 20.0 : (this.up ? this.verticalSpeed : -this.verticalSpeed) / 20.0;
      double rate = asked ? this.acceleration : Math.min(1.0, this.acceleration * 1.5);
      this.curX = this.curX + (this.dir[0] * perTick - this.curX) * rate;
      this.curZ = this.curZ + (this.dir[1] * perTick - this.curZ) * rate;
      this.curY = this.curY + (targetY - this.curY) * Math.min(1.0, this.acceleration * 1.5);
      if (!asked && Math.abs(this.curX) < 1.0E-4 && Math.abs(this.curZ) < 1.0E-4) {
         this.curX = this.curZ = 0.0;
      }

      vehicle.setDeltaMovement(this.curX, this.curY, this.curZ);
      vehicle.fallDistance = 0.0;
      this.preX = vehicle.getX();
      this.preY = vehicle.getY();
      this.preZ = vehicle.getZ();
      this.moved = true;
      return new Vec3(this.curX, this.curY, this.curZ);
   }

   void afterTick(Entity vehicle) {
      if (this.moved) {
         this.moved = false;
         double dx = vehicle.getX() - this.preX;
         double dy = vehicle.getY() - this.preY;
         double dz = vehicle.getZ() - this.preZ;
         if (Math.abs(dx) < Math.abs(this.curX) * 0.5) {
            this.curX = dx;
         }

         if (Math.abs(dy) < Math.abs(this.curY) * 0.5) {
            this.curY = dy;
         }

         if (Math.abs(dz) < Math.abs(this.curZ) * 0.5) {
            this.curZ = dz;
         }
      }
   }
}
