package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module implements MoveHooks.Adjuster {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWhen = this.settings.createGroup("When");
   private final Setting<Speed.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Vanilla: multiplies the distance you walk each tick, keeping vanilla's feel. Strafe: your keys set your speed and direction exactly, in the air too."
                  ))
               .defaultValue(Speed.Mode.Vanilla))
            .build()
      );
   private final Setting<Double> multiplier = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("multiplier")
            .description("How many times your normal speed. 1.2 to 1.3 is subtle; much higher is plainly visible and easy to flag.")
            .defaultValue(1.4)
            .range(1.0, 10.0)
            .sliderRange(1.0, 5.0)
            .decimalPlaces(2)
            .visible(() -> this.mode.get() == Speed.Mode.Vanilla)
            .build()
      );
   private final Setting<Double> strafeSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("Blocks per second. Walking is about 4.3, sprinting 5.6, a sprint-jump about 7.")
            .defaultValue(7.0)
            .range(1.0, 60.0)
            .sliderRange(2.0, 30.0)
            .decimalPlaces(1)
            .visible(() -> this.mode.get() == Speed.Mode.Strafe)
            .build()
      );
   private final Setting<Boolean> autoJump = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-jump")
            .description("Jump by itself whenever you are walking on the ground, so you keep bunny-hopping without pressing jump.")
            .defaultValue(false)
            .visible(() -> this.mode.get() == Speed.Mode.Strafe)
            .build()
      );
   private final Setting<Boolean> onlyOnGround = this.sgWhen
      .add(
         new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Only speed up while you are standing on something (not in the air).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> inWater = this.sgWhen
      .add(new BoolSetting.Builder().name("in-water").description("Also speed up in water and lava.").defaultValue(false).build());
   private final Setting<Boolean> whileSneaking = this.sgWhen
      .add(
         new BoolSetting.Builder()
            .name("while-sneaking")
            .description("Also speed up while sneaking. Off keeps sneaking at its normal slow, edge-safe pace.")
            .defaultValue(false)
            .build()
      );
   private final double[] dir = new double[2];

   public Speed() {
      super(
         SpecterClientAddon.BLATANT,
         "speed",
         "Move faster than normal: a multiplier on your walking, or direct air-strafe control. Never touches the tick speed. Servers tolerate some extra speed but anti-cheats compare it with what walking and jumping can reach."
      );
   }

   @Override
   public void onActivate() {
      MoveHooks.add(this);
   }

   @Override
   public void onDeactivate() {
      MoveHooks.remove(this);
   }

   private boolean applies(LocalPlayer player) {
      if (player.isPassenger() || player.isFallFlying() || player.isSpectator() || player.getAbilities().flying) {
         return false;
      } else if (player.onClimbable() || player.isSwimming()) {
         return false;
      } else if (this.onlyOnGround.get() && !player.onGround()) {
         return false;
      } else if (this.inWater.get() || !player.isInWater() && !player.isInLava()) {
         return !this.whileSneaking.get() && player.isShiftKeyDown() ? false : !Modules.get().isActive(Fly.class) || !Modules.get().get(Fly.class).isFlying();
      } else {
         return false;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mode.get() == Speed.Mode.Strafe && this.autoJump.get()) {
         if (this.applies(player) && player.onGround() && MoveInput.anyHorizontal() && !MoveInput.jump() && !player.isUsingItem()) {
            player.jumpFromGround();
         }
      }
   }

   @Override
   public Vec3 adjust(Entity entity, MoverType type, Vec3 movement) {
      if (type == MoverType.SELF && entity == this.mc.player) {
         LocalPlayer player = this.mc.player;
         if (!this.applies(player)) {
            return movement;
         } else if (this.mode.get() != Speed.Mode.Vanilla) {
            if (!MoveInput.direction(player.getYRot(), this.dir)) {
               return movement;
            } else {
               double perTick = this.strafeSpeed.get() / 20.0;
               return new Vec3(this.dir[0] * perTick, movement.y, this.dir[1] * perTick);
            }
         } else if (!MoveInput.anyHorizontal()) {
            return movement;
         } else {
            double m = this.multiplier.get();
            return new Vec3(movement.x * m, movement.y, movement.z * m);
         }
      } else {
         return movement;
      }
   }

   @Override
   public String getInfoString() {
      return this.mode.get() == Speed.Mode.Vanilla ? String.format("%.2fx", this.multiplier.get()) : "Strafe";
   }

   public static enum Mode {
      Vanilla("Vanilla"),
      Strafe("Strafe");

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
