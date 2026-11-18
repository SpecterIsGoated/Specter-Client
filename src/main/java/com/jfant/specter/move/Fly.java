package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.notify.Notifications;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

public class Fly extends Module implements MoveHooks.Adjuster {
   private static final double BLOCKS_PER_FLY_SPEED = 222.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSpeed = this.settings.createGroup("Speed");
   private final SettingGroup sgActivation = this.settings.createGroup("Activation");
   private final SettingGroup sgAntiKick = this.settings.createGroup("Anti-Kick");
   private final Setting<Fly.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description(
                        "Velocity: your keys set your movement directly each tick and gravity is ignored; nothing extra is sent to the server. Abilities: turns on the creative flying flag (the server is told when you double-tap jump or land)."
                     ))
                  .defaultValue(Fly.Mode.Velocity))
               .onChanged(m -> {
                  if (this.isActive() && Utils.canUpdate()) {
                     this.release();
                     this.lastPlayer = null;
                  }
               }))
            .build()
      );
   private final Setting<Boolean> noFallOnStop = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("no-fall-on-stop")
            .description(
               "When you stop flying in mid-air, mark your movement as on the ground until you land, so the fall does not hurt. A server that checks for a ground under you will notice."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> horizontalSpeed = this.sgSpeed
      .add(
         new DoubleSetting.Builder()
            .name("horizontal-speed")
            .description("Blocks per second sideways. Walking is about 4.3, sprinting 5.6. Higher is more likely to be flagged.")
            .defaultValue(10.0)
            .range(0.5, 100.0)
            .sliderRange(1.0, 40.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Double> verticalSpeed = this.sgSpeed
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Blocks per second up (jump) and down (sneak).")
            .defaultValue(8.0)
            .range(0.5, 60.0)
            .sliderRange(1.0, 30.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Double> sprintMultiplier = this.sgSpeed
      .add(
         new DoubleSetting.Builder()
            .name("sprint-multiplier")
            .description("Your speed is multiplied by this while the sprint key is held. 1 turns it off.")
            .defaultValue(1.5)
            .range(1.0, 4.0)
            .sliderRange(1.0, 4.0)
            .decimalPlaces(2)
            .build()
      );
   private final Setting<Double> acceleration = this.sgSpeed
      .add(
         new DoubleSetting.Builder()
            .name("acceleration")
            .description("How quickly you pick up speed: the share of the missing speed gained each tick. Low is a slow, floaty build-up, 1 is instant.")
            .defaultValue(0.35)
            .range(0.02, 1.0)
            .sliderRange(0.02, 1.0)
            .decimalPlaces(2)
            .visible(() -> this.mode.get() == Fly.Mode.Velocity)
            .build()
      );
   private final Setting<Double> deceleration = this.sgSpeed
      .add(
         new DoubleSetting.Builder()
            .name("deceleration")
            .description("How quickly you slow down when you let go of the keys, same scale as acceleration. Low drifts to a stop, 1 stops dead.")
            .defaultValue(0.5)
            .range(0.02, 1.0)
            .sliderRange(0.02, 1.0)
            .decimalPlaces(2)
            .visible(() -> this.mode.get() == Fly.Mode.Velocity)
            .build()
      );
   private final Setting<Fly.Activation> activation = this.sgActivation
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("activation"))
                     .description(
                        "When flying actually happens while the module is on. Always: right away. Hold Key: only while the key below is held. Double-Tap Jump: double-tap jump to start, double-tap again to stop."
                     ))
                  .defaultValue(Fly.Activation.Always))
               .onChanged(a -> {
                  this.tapEngaged = false;
                  if (a == Fly.Activation.HoldKey && !this.holdKey.get().isSet()) {
                     Notifications.keyed(this, Notifications.Kind.WARNING, "Fly", "Set the hold key in Fly's settings.");
                  }
               }))
            .build()
      );
   private final Setting<Keybind> holdKey = this.sgActivation
      .add(
         new KeybindSetting.Builder()
            .name("hold-key")
            .description("The key to hold to fly. Nothing happens while it is not set.")
            .defaultValue(Keybind.none())
            .visible(() -> this.activation.get() == Fly.Activation.HoldKey)
            .build()
      );
   private final Setting<Integer> doubleTapWindow = this.sgActivation
      .add(
         new IntSetting.Builder()
            .name("double-tap-window")
            .description("Longest time, in milliseconds, between the two jump presses.")
            .defaultValue(300)
            .range(100, 800)
            .sliderRange(150, 600)
            .visible(() -> this.activation.get() == Fly.Activation.DoubleTapJump)
            .build()
      );
   private final Setting<Boolean> stopOnLanding = this.sgActivation
      .add(
         new BoolSetting.Builder()
            .name("stop-on-landing")
            .description("Stop flying when you touch the ground.")
            .defaultValue(true)
            .visible(() -> this.activation.get() == Fly.Activation.DoubleTapJump)
            .build()
      );
   private final Setting<Boolean> antiKick = this.sgAntiKick
      .add(
         new BoolSetting.Builder()
            .name("anti-kick")
            .description(
               "Every so often dip a little and come back up, so the server does not see you hovering and kick you for flying. Off if you only fly where flying is allowed."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> antiKickInterval = this.sgAntiKick
      .add(
         new IntSetting.Builder()
            .name("interval")
            .description("Ticks between dips (20 ticks = 1 second). The kick comes after about 80 ticks of hovering, so stay well under that.")
            .defaultValue(30)
            .range(5, 70)
            .sliderRange(10, 60)
            .visible(this.antiKick::get)
            .build()
      );
   private final Setting<Double> antiKickAmount = this.sgAntiKick
      .add(
         new DoubleSetting.Builder()
            .name("amount")
            .description(
               "Blocks to drop each dip. The server counts a tick as falling from 0.032 blocks down; the dip returns the same distance on the next tick."
            )
            .defaultValue(0.04)
            .range(0.032, 0.5)
            .sliderRange(0.032, 0.2)
            .decimalPlaces(3)
            .visible(this.antiKick::get)
            .build()
      );
   private LocalPlayer lastPlayer;
   private boolean engaged;
   private boolean tapEngaged;
   private boolean jumpWasDown;
   private int tick;
   private int lastJumpTick = -1000;
   private int groundTicks;
   private double curX;
   private double curY;
   private double curZ;
   private double preX;
   private double preY;
   private double preZ;
   private boolean moved;
   private final DipTimer dip = new DipTimer();
   private boolean abilitiesTaken;
   private volatile boolean savedAllow;
   private volatile boolean savedFlying;
   private volatile float savedSpeed;
   private final double[] dir = new double[2];

   public Fly() {
      super(
         SpecterClientAddon.BLATANT,
         "fly",
         "Fly with your keys: speed, acceleration and when it engages are all set here. Client-side movement the server only sees as positions: vanilla kicks hovering after about 4 seconds (anti-kick helps), and anti-cheats flag flying and speed regardless."
      );
   }

   @Override
   public void onActivate() {
      MoveHooks.add(this);
      this.resetState();
      this.lastPlayer = this.mc.player;
   }

   @Override
   public void onDeactivate() {
      MoveHooks.remove(this);
      this.release();
      this.tapEngaged = false;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.resetState();
      this.abilitiesTaken = false;
      this.lastPlayer = null;
   }

   private void resetState() {
      this.engaged = false;
      this.tapEngaged = false;
      this.jumpWasDown = false;
      this.groundTicks = 0;
      this.curX = this.curY = this.curZ = 0.0;
      this.moved = false;
      this.dip.reset();
   }

   public boolean isFlying() {
      return this.engaged;
   }

   private boolean canFly(LocalPlayer player) {
      return !player.isSpectator() && !player.isPassenger() && !player.isFallFlying() && player.isAlive() && !player.isDeadOrDying();
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null) {
         this.tick++;
         if (player != this.lastPlayer) {
            this.lastPlayer = player;
            this.resetState();
            this.abilitiesTaken = false;
         }

         this.trackActivation(player);
         boolean want = this.canFly(player) && this.wantsEngaged(player);
         if (want && !this.engaged) {
            this.engage(player);
         } else if (!want && this.engaged) {
            this.disengage(player);
         }

         if (this.engaged && this.mode.get() == Fly.Mode.Abilities) {
            this.holdAbilities(player);
         }
      }
   }

   private boolean wantsEngaged(LocalPlayer player) {
      return switch ((Fly.Activation)this.activation.get()) {
         case Always -> true;
         case HoldKey -> this.mc.screen == null && this.holdKey.get().isSet() && this.holdKey.get().isPressed();
         case DoubleTapJump -> this.tapEngaged;
      };
   }

   private void trackActivation(LocalPlayer player) {
      if (this.activation.get() == Fly.Activation.DoubleTapJump) {
         boolean jump = MoveInput.jump() && this.mc.screen == null;
         if (jump && !this.jumpWasDown) {
            int windowTicks = Math.max(1, this.doubleTapWindow.get() / 50);
            if (this.tick - this.lastJumpTick <= windowTicks) {
               this.tapEngaged = !this.tapEngaged;
               this.lastJumpTick = -1000;
            } else {
               this.lastJumpTick = this.tick;
            }
         }

         this.jumpWasDown = jump;
         if (this.tapEngaged && this.stopOnLanding.get() && player.onGround() && !jump) {
            if (++this.groundTicks >= 3) {
               this.tapEngaged = false;
            }
         } else {
            this.groundTicks = 0;
         }
      }
   }

   private void engage(LocalPlayer player) {
      this.engaged = true;
      LandingGuard.stop();
      this.dip.reset();
      this.moved = false;
      if (this.mode.get() == Fly.Mode.Abilities) {
         Abilities abilities = player.getAbilities();
         this.savedAllow = abilities.mayfly;
         this.savedFlying = abilities.flying;
         this.savedSpeed = abilities.getFlyingSpeed();
         this.abilitiesTaken = true;
      } else {
         Vec3 v = player.getDeltaMovement();
         this.curX = v.x;
         this.curY = Math.max(v.y, 0.0);
         this.curZ = v.z;
      }
   }

   private void disengage(LocalPlayer player) {
      boolean wasEngaged = this.engaged;
      this.engaged = false;
      this.giveBackAbilities(player);
      if (wasEngaged && this.noFallOnStop.get() && !player.onGround() && !player.getAbilities().instabuild) {
         LandingGuard.start();
      }

      this.curX = this.curY = this.curZ = 0.0;
   }

   private void release() {
      LocalPlayer player = this.mc.player;
      if (player != null && this.engaged) {
         this.disengage(player);
      } else if (player != null) {
         this.giveBackAbilities(player);
      }

      this.engaged = false;
   }

   private void holdAbilities(LocalPlayer player) {
      Abilities abilities = player.getAbilities();
      abilities.mayfly = true;
      abilities.flying = true;
      double speed = this.horizontalSpeed.get() * (player.isSprinting() ? this.sprintMultiplier.get() : 1.0);
      float flySpeed = (float)(speed / 222.0 / (player.isSprinting() ? 2 : 1));
      abilities.setFlyingSpeed(flySpeed);
   }

   private void giveBackAbilities(LocalPlayer player) {
      if (this.abilitiesTaken) {
         this.abilitiesTaken = false;
         Abilities abilities = player.getAbilities();
         abilities.mayfly = this.savedAllow;
         abilities.flying = this.savedFlying && this.savedAllow;
         abilities.setFlyingSpeed(this.savedSpeed);
      }
   }

   @EventHandler
   private void onAbilitiesPacket(PacketEvent.Receive event) {
      if (this.abilitiesTaken && event.packet instanceof ClientboundPlayerAbilitiesPacket packet) {
         this.savedAllow = packet.canFly();
         this.savedFlying = packet.isFlying();
         this.savedSpeed = packet.getFlyingSpeed();
      }
   }

   @Override
   public Vec3 adjust(Entity entity, MoverType type, Vec3 movement) {
      if (this.engaged && entity == this.mc.player && type == MoverType.SELF) {
         LocalPlayer player = this.mc.player;
         double x = movement.x;
         double y = movement.y;
         double z = movement.z;
         if (this.mode.get() == Fly.Mode.Velocity) {
            this.steer(player);
            x = this.curX;
            y = this.curY;
            z = this.curZ;
            player.setDeltaMovement(this.curX, this.curY, this.curZ);
            player.fallDistance = 0.0;
            this.preX = player.getX();
            this.preY = player.getY();
            this.preZ = player.getZ();
            this.moved = true;
         } else {
            int vertical = MoveInput.vertical();
            if (vertical != 0) {
               y = vertical * this.verticalSpeed.get() / 20.0;
            }
         }

         y += this.dip.next(this.antiKick.get(), this.antiKickInterval.get(), this.antiKickAmount.get());
         return new Vec3(x, y, z);
      } else {
         return movement;
      }
   }

   private void steer(LocalPlayer player) {
      boolean asked = MoveInput.direction(player.getYRot(), this.dir);
      double perTick = this.horizontalSpeed.get() / 20.0 * (!MoveInput.sprintKey() && !player.isSprinting() ? 1.0 : this.sprintMultiplier.get());
      double targetX = this.dir[0] * perTick;
      double targetZ = this.dir[1] * perTick;
      double targetY = MoveInput.vertical() * this.verticalSpeed.get() / 20.0;
      double accel = this.acceleration.get();
      double decel = this.deceleration.get();
      double horizontalRate = asked ? accel : decel;
      double verticalRate = MoveInput.vertical() != 0 ? accel : decel;
      this.curX = this.curX + (targetX - this.curX) * horizontalRate;
      this.curZ = this.curZ + (targetZ - this.curZ) * horizontalRate;
      this.curY = this.curY + (targetY - this.curY) * verticalRate;
      if (!asked && Math.abs(this.curX) < 1.0E-4 && Math.abs(this.curZ) < 1.0E-4) {
         this.curX = this.curZ = 0.0;
      }

      if (MoveInput.vertical() == 0 && Math.abs(this.curY) < 1.0E-4) {
         this.curY = 0.0;
      }
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (this.engaged && this.moved && this.mode.get() == Fly.Mode.Velocity) {
            double dx = player.getX() - this.preX;
            double dy = player.getY() - this.preY;
            double dz = player.getZ() - this.preZ;
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

         this.moved = false;
      }
   }

   @Override
   public String getInfoString() {
      return this.mode.get().toString();
   }

   public static enum Activation {
      Always("Always"),
      HoldKey("Hold Key"),
      DoubleTapJump("Double-Tap Jump");

      private final String title;

      private Activation(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Mode {
      Velocity("Velocity"),
      Abilities("Abilities");

      private final String title;

      private Mode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
