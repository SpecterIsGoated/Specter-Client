package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

public class BoatFly extends Module implements MoveHooks.Adjuster {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgControl = this.settings.createGroup("Control");
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final Setting<Double> horizontalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("horizontal-speed")
            .description(
               "Blocks per second sideways. A boat on water goes about 8, on ice about 40. A server notices anything much faster than the vehicle can go."
            )
            .defaultValue(15.0)
            .range(0.5, 120.0)
            .sliderRange(1.0, 60.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Double> verticalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Blocks per second up (jump) and down (sprint or the down key).")
            .defaultValue(8.0)
            .range(0.5, 60.0)
            .sliderRange(1.0, 30.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Double> fallSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fall-speed")
            .description("Blocks per second the vehicle sinks when you press neither up nor down. 0 hovers in place.")
            .defaultValue(0.0)
            .range(0.0, 20.0)
            .sliderRange(0.0, 5.0)
            .decimalPlaces(2)
            .build()
      );
   private final Setting<Double> acceleration = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("acceleration")
            .description(
               "How quickly it reaches the speed you ask for: the share of the missing speed gained each tick. Low is smooth and floaty, 1 is instant."
            )
            .defaultValue(0.4)
            .range(0.02, 1.0)
            .sliderRange(0.02, 1.0)
            .decimalPlaces(2)
            .build()
      );
   private final Setting<Boolean> lockYaw = this.sgControl
      .add(
         new BoolSetting.Builder()
            .name("lock-yaw")
            .description(
               "The vehicle faces where you look and the walking keys move it relative to that (strafing works). Off: it steers the normal way and only forward and back push it."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> downWithSprint = this.sgControl
      .add(
         new BoolSetting.Builder()
            .name("down-with-sprint")
            .description("The sprint key goes down. (Sneak cannot: it gets you off the vehicle.)")
            .defaultValue(true)
            .build()
      );
   private final Setting<Keybind> downKey = this.sgControl
      .add(
         new KeybindSetting.Builder()
            .name("down-key")
            .description("An extra key that goes down. Not set means only the sprint option above.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Boolean> otherVehicles = this.sgControl
      .add(
         new BoolSetting.Builder()
            .name("other-vehicles")
            .description("Also drive horses, pigs, striders, camels and other mounts you control. Their own jumping and gaits are ignored. Off: boats only.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> cancelCorrections = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("cancel-server-corrections")
            .description(
               "Ignore the server's messages that move the vehicle back. You keep flying where it would stop you, but you will be out of step with the server and can be put back, or fall, when it catches up."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> antiKick = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("anti-kick")
            .description("Dip a little every few ticks so the server does not see a hovering vehicle and kick you for flying.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> antiKickInterval = this.sgSafety
      .add(
         new IntSetting.Builder()
            .name("anti-kick-interval")
            .description("Ticks between dips (20 = 1 second). The kick comes after about 80 ticks of hovering.")
            .defaultValue(30)
            .range(5, 70)
            .sliderRange(10, 60)
            .visible(this.antiKick::get)
            .build()
      );
   private final Setting<Double> antiKickAmount = this.sgSafety
      .add(
         new DoubleSetting.Builder()
            .name("anti-kick-amount")
            .description("Blocks to drop on each dip (the server counts 0.032 and more); the next tick brings it back.")
            .defaultValue(0.04)
            .range(0.032, 0.5)
            .sliderRange(0.032, 0.2)
            .decimalPlaces(3)
            .visible(this.antiKick::get)
            .build()
      );
   private final Setting<Boolean> noFallOnDismount = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("no-fall-on-dismount")
            .description(
               "If you get off while in the air, mark your movement as on the ground until you land so the fall does not hurt. A server that checks for a ground under you will notice."
            )
            .defaultValue(true)
            .build()
      );
   private final VehicleDrive drive = new VehicleDrive();
   private final DipTimer dip = new DipTimer();
   private Entity lastVehicle;

   public BoatFly() {
      super(
         SpecterClientAddon.BLATANT,
         "boat-fly",
         "Fly the boat you are in: it goes where you look, up on jump, down on sprint. The server rolls vehicles back and kicks hovering ones, and anti-cheats flag vehicle speed; anti-kick and cancelling corrections help for a while but desync you."
      );
   }

   @Override
   public void onActivate() {
      MoveHooks.add(this);
      this.drive.reset();
      this.dip.reset();
      this.lastVehicle = null;
   }

   @Override
   public void onDeactivate() {
      MoveHooks.remove(this);
      this.stop(this.mc.player);
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.drive.reset();
      this.dip.reset();
      this.lastVehicle = null;
   }

   private boolean drives(Entity vehicle) {
      LocalPlayer player = this.mc.player;
      if (player == null || vehicle == null || player.getControlledVehicle() != vehicle) {
         return false;
      } else {
         return vehicle instanceof AbstractBoat ? true : this.otherVehicles.get() && vehicle instanceof LivingEntity;
      }
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null) {
         Entity vehicle = this.drives(player.getVehicle()) ? player.getVehicle() : null;
         if (this.lastVehicle != null && vehicle != this.lastVehicle) {
            this.stop(player);
         }

         this.lastVehicle = vehicle;
         if (vehicle != null) {
            this.drive.horizontalSpeed = this.horizontalSpeed.get();
            this.drive.verticalSpeed = this.verticalSpeed.get();
            this.drive.idleFall = this.fallSpeed.get();
            this.drive.acceleration = this.acceleration.get();
            this.drive.lockYaw = this.lockYaw.get();
            this.drive.up = MoveInput.jump();
            this.drive.down = this.downWithSprint.get() && MoveInput.sprintKey() || this.downKey.get().isSet() && this.downKey.get().isPressed();
            LandingGuard.stop();
         }
      }
   }

   private void stop(LocalPlayer player) {
      if (this.lastVehicle != null) {
         this.lastVehicle.setDeltaMovement(Vec3.ZERO);
         if (this.noFallOnDismount.get() && player != null && !player.onGround() && !player.isPassenger()) {
            LandingGuard.start();
         }
      }

      this.lastVehicle = null;
      this.drive.reset();
      this.dip.reset();
   }

   @Override
   public Vec3 adjust(Entity entity, MoverType type, Vec3 movement) {
      if (type == MoverType.SELF && entity == this.lastVehicle && this.drives(entity)) {
         Vec3 driven = this.drive.step(entity, this.mc.player);
         double dipped = this.dip.next(this.antiKick.get(), this.antiKickInterval.get(), this.antiKickAmount.get());
         return dipped == 0.0 ? driven : new Vec3(driven.x, driven.y + dipped, driven.z);
      } else {
         return movement;
      }
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      if (this.lastVehicle != null) {
         this.drive.afterTick(this.lastVehicle);
      }
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      if (this.cancelCorrections.get()) {
         Entity vehicle = this.lastVehicle;
         if (vehicle != null) {
            if (event.packet instanceof ClientboundMoveVehiclePacket) {
               event.cancel();
            } else if (event.packet instanceof ClientboundTeleportEntityPacket p && p.id() == vehicle.getId()) {
               event.cancel();
            } else if (event.packet instanceof ClientboundEntityPositionSyncPacket p && p.id() == vehicle.getId()) {
               event.cancel();
            }
         }
      }
   }
}
