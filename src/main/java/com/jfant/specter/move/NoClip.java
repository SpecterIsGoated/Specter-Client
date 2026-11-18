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
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

public class NoClip extends Module implements MoveHooks.Adjuster {
   private static NoClip instance;
   private final SettingGroup sgEntities = this.settings.createGroup("Entities");
   private final SettingGroup sgVehicle = this.settings.createGroup("Vehicle Phase");
   private final Setting<Boolean> entityCollision = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("entity-no-clip")
            .description("Do not collide with entities that have a solid body: walk through them and do not stand on them.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> boats = this.sgEntities
      .add(new BoolSetting.Builder().name("boats").description("Pass through boats and rafts.").defaultValue(true).visible(this.entityCollision::get).build());
   private final Setting<Boolean> minecarts = this.sgEntities
      .add(new BoolSetting.Builder().name("minecarts").description("Pass through minecarts.").defaultValue(true).visible(this.entityCollision::get).build());
   private final Setting<Boolean> shulkers = this.sgEntities
      .add(new BoolSetting.Builder().name("shulkers").description("Pass through shulkers.").defaultValue(true).visible(this.entityCollision::get).build());
   private final Setting<Boolean> others = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("other-solid-entities")
            .description("Pass through any other entity with a solid body (end crystals and whatever else the game makes solid).")
            .defaultValue(true)
            .visible(this.entityCollision::get)
            .build()
      );
   private final Setting<Boolean> notPushed = this.sgEntities
      .add(
         new BoolSetting.Builder().name("not-pushed").description("Mobs, players and other entities crowding you do not shove you.").defaultValue(true).build()
      );
   private final Setting<Boolean> notPushing = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("not-pushing")
            .description("You do not shove them either (as far as your own screen shows; the server still pushes its own copy).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> vehiclePhase = this.sgVehicle
      .add(
         new BoolSetting.Builder()
            .name("vehicle-phase")
            .description(
               "While you ride a boat or minecart it passes through blocks, and it goes where your keys say. Minecarts on rails still follow the rails."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> horizontalSpeed = this.sgVehicle
      .add(
         new DoubleSetting.Builder()
            .name("horizontal-speed")
            .description("Blocks per second sideways while phasing. Ignored when Boat Fly is also on (it sets the speed then).")
            .defaultValue(10.0)
            .range(0.5, 60.0)
            .sliderRange(1.0, 40.0)
            .decimalPlaces(1)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Double> verticalSpeed = this.sgVehicle
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Blocks per second up and down while phasing.")
            .defaultValue(6.0)
            .range(0.5, 40.0)
            .sliderRange(1.0, 20.0)
            .decimalPlaces(1)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Double> idleFall = this.sgVehicle
      .add(
         new DoubleSetting.Builder()
            .name("fall-speed")
            .description("Blocks per second the vehicle sinks when you press neither up nor down. 0 holds it where it is.")
            .defaultValue(0.0)
            .range(0.0, 20.0)
            .sliderRange(0.0, 5.0)
            .decimalPlaces(2)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Boolean> lockYaw = this.sgVehicle
      .add(
         new BoolSetting.Builder()
            .name("lock-yaw")
            .description(
               "The vehicle faces where you look and the walking keys move it relative to that. Off: only forward and back push it, along the way it already faces."
            )
            .defaultValue(true)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Boolean> upWithJump = this.sgVehicle
      .add(new BoolSetting.Builder().name("up-with-jump").description("The jump key goes up.").defaultValue(true).visible(this.vehiclePhase::get).build());
   private final Setting<Keybind> upKey = this.sgVehicle
      .add(
         new KeybindSetting.Builder()
            .name("up-key")
            .description("An extra key that goes up. Not set means only the jump option above.")
            .defaultValue(Keybind.none())
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Boolean> downWithSprint = this.sgVehicle
      .add(
         new BoolSetting.Builder()
            .name("down-with-sprint")
            .description("The sprint key goes down. (Sneak cannot: it gets you off the vehicle.)")
            .defaultValue(true)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Keybind> downKey = this.sgVehicle
      .add(
         new KeybindSetting.Builder()
            .name("down-key")
            .description("An extra key that goes down. Not set means only the sprint option above.")
            .defaultValue(Keybind.none())
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Boolean> cancelCorrections = this.sgVehicle
      .add(
         new BoolSetting.Builder()
            .name("cancel-server-corrections")
            .description(
               "Ignore the server's messages that move the vehicle back out of the blocks. You stay inside them, but out of step with the server, which can put you back later."
            )
            .defaultValue(false)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Boolean> antiKick = this.sgVehicle
      .add(
         new BoolSetting.Builder()
            .name("anti-kick")
            .description("Dip a little every few ticks so the server does not see a hovering vehicle and kick you for flying.")
            .defaultValue(true)
            .visible(this.vehiclePhase::get)
            .build()
      );
   private final Setting<Integer> antiKickInterval = this.sgVehicle
      .add(
         new IntSetting.Builder()
            .name("anti-kick-interval")
            .description("Ticks between dips (20 = 1 second). The kick comes after about 80 ticks of hovering.")
            .defaultValue(30)
            .range(5, 70)
            .sliderRange(10, 60)
            .visible(() -> this.vehiclePhase.get() && this.antiKick.get())
            .build()
      );
   private final Setting<Double> antiKickAmount = this.sgVehicle
      .add(
         new DoubleSetting.Builder()
            .name("anti-kick-amount")
            .description("Blocks to drop on each dip (the server counts 0.032 and more); the next tick brings it back.")
            .defaultValue(0.04)
            .range(0.032, 0.5)
            .sliderRange(0.032, 0.2)
            .decimalPlaces(3)
            .visible(() -> this.vehiclePhase.get() && this.antiKick.get())
            .build()
      );
   private final VehicleDrive drive = new VehicleDrive();
   private final DipTimer dip = new DipTimer();
   private Entity phased;

   public NoClip() {
      super(
         SpecterClientAddon.BLATANT,
         "no-clip",
         "Move through boats, minecarts, shulkers and other solid entities, and (riding) phase your boat or minecart through blocks. Not walking through blocks on foot: a server checks that. A vanilla server still collides you with entities and puts you back, and refuses vehicles entering blocks, so both parts can rubber-band."
      );
      instance = this;
   }

   public static boolean ignoresCollisionWith(Entity other) {
      NoClip noClip = instance;
      if (noClip == null || !noClip.isActive() || !noClip.entityCollision.get()) {
         return false;
      } else if (other instanceof AbstractBoat) {
         return noClip.boats.get();
      } else if (other instanceof AbstractMinecart) {
         return noClip.minecarts.get();
      } else {
         return other instanceof Shulker ? noClip.shulkers.get() : noClip.others.get();
      }
   }

   public static boolean blocksPush(Entity pushed, Entity pusher) {
      NoClip noClip = instance;
      if (noClip != null && noClip.isActive()) {
         Entity player = noClip.mc.player;
         return player == null ? false : pushed == player && noClip.notPushed.get() || pusher == player && noClip.notPushing.get();
      } else {
         return false;
      }
   }

   @Override
   public void onActivate() {
      MoveHooks.add(this);
      this.drive.reset();
      this.dip.reset();
   }

   @Override
   public void onDeactivate() {
      MoveHooks.remove(this);
      this.giveBack();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.phased = null;
      this.drive.reset();
      this.dip.reset();
   }

   private void giveBack() {
      if (this.phased != null) {
         this.phased.noPhysics = false;
      }

      this.phased = null;
      this.drive.reset();
      this.dip.reset();
   }

   private Entity phaseable() {
      LocalPlayer player = this.mc.player;
      if (this.vehiclePhase.get() && player != null) {
         Entity vehicle = player.getControlledVehicle();
         return !(vehicle instanceof AbstractBoat) && !(vehicle instanceof AbstractMinecart) ? null : vehicle;
      } else {
         return null;
      }
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      Entity vehicle = this.phaseable();
      if (this.phased != null && this.phased != vehicle) {
         this.giveBack();
      }

      if (vehicle != null) {
         this.drive.horizontalSpeed = this.horizontalSpeed.get();
         this.drive.verticalSpeed = this.verticalSpeed.get();
         this.drive.idleFall = this.idleFall.get();
         this.drive.lockYaw = this.lockYaw.get();
         this.drive.up = this.upWithJump.get() && MoveInput.jump() || this.upKey.get().isSet() && this.upKey.get().isPressed();
         this.drive.down = this.downWithSprint.get() && MoveInput.sprintKey() || this.downKey.get().isSet() && this.downKey.get().isPressed();
      }
   }

   @Override
   public Vec3 adjust(Entity entity, MoverType type, Vec3 movement) {
      if (type != MoverType.SELF) {
         return movement;
      } else {
         Entity vehicle = this.phaseable();
         if (vehicle != null && entity == vehicle) {
            vehicle.noPhysics = true;
            this.phased = vehicle;
            if (Modules.get().isActive(BoatFly.class)) {
               return movement;
            } else {
               Vec3 driven = this.drive.step(vehicle, this.mc.player);
               double dipped = this.dip.next(this.antiKick.get(), this.antiKickInterval.get(), this.antiKickAmount.get());
               return dipped == 0.0 ? driven : new Vec3(driven.x, driven.y + dipped, driven.z);
            }
         } else {
            return movement;
         }
      }
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      if (this.cancelCorrections.get() && this.phased != null) {
         Entity vehicle = this.phased;
         if (event.packet instanceof ClientboundMoveVehiclePacket) {
            event.cancel();
         } else if (event.packet instanceof ClientboundTeleportEntityPacket p && p.id() == vehicle.getId()) {
            event.cancel();
         } else if (event.packet instanceof ClientboundEntityPositionSyncPacket p && p.id() == vehicle.getId()) {
            event.cancel();
         }
      }
   }

   @Override
   public String getInfoString() {
      return (this.entityCollision.get() ? "Entities" : "")
         + (this.entityCollision.get() && this.vehiclePhase.get() ? " + " : "")
         + (this.vehiclePhase.get() ? "Vehicle" : "");
   }
}
