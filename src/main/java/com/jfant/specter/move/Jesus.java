package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.CanWalkOnFluidEvent;
import com.jfant.specter.core.events.world.CollisionShapeEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class Jesus extends Module {
   private final SettingGroup sgWater = this.settings.getDefaultGroup();
   private final SettingGroup sgLava = this.settings.createGroup("Lava");
   private final SettingGroup sgDip = this.settings.createGroup("Dipping");
   private final Setting<Boolean> water = this.sgWater.add(new BoolSetting.Builder().name("water").description("Walk on water.").defaultValue(true).build());
   private final Setting<Boolean> lava = this.sgLava
      .add(
         new BoolSetting.Builder()
            .name("lava")
            .description("Walk on lava. Dangerous: if the walk-on rule lets go (lag, a flag from the server) you are in lava.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> lavaNeedsResistance = this.sgLava
      .add(
         new BoolSetting.Builder()
            .name("only-with-fire-resistance")
            .description("Only walk on lava while you have Fire Resistance with at least 15 seconds left, so a slip is survivable.")
            .defaultValue(true)
            .visible(this.lava::get)
            .build()
      );
   private final Setting<Boolean> flowing = this.sgWater
      .add(
         new BoolSetting.Builder()
            .name("flowing-too")
            .description(
               "Also walk on flowing (not source) liquid. This makes those blocks solid on your screen for everything, so boats and mobs beside you may bump into them too."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> rise = this.sgWater
      .add(
         new BoolSetting.Builder()
            .name("rise-to-surface")
            .description("If you are already in the liquid, float up until you stand on top of it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> dipOnSneak = this.sgDip
      .add(
         new BoolSetting.Builder().name("dip-on-sneak").description("Hold sneak to sink into the liquid instead of standing on it.").defaultValue(true).build()
      );
   private final Setting<Integer> dipFallHeight = this.sgDip
      .add(
         new IntSetting.Builder()
            .name("dip-fall-height")
            .description(
               "Fall further than this many blocks and you go into the liquid instead of landing on it (a fall into water is safe; a landing on its surface counts as a landing on the ground). 0 means a fall never dips you."
            )
            .defaultValue(4)
            .range(0, 255)
            .sliderRange(0, 30)
            .build()
      );
   private final Setting<Boolean> dipWhenBurning = this.sgDip
      .add(new BoolSetting.Builder().name("dip-when-burning").description("Go into water while you are on fire, to put it out.").defaultValue(true).build());

   public Jesus() {
      super(
         SpecterClientAddon.BLATANT,
         "jesus",
         "Walk on water (and lava, off by default). Client-side only: a vanilla server accepts you standing over water, but anti-cheats flag it, and lava is risky. Sneak or a long fall puts you in."
      );
   }

   private boolean solid(Fluid fluid) {
      LocalPlayer player = this.mc.player;
      if (player == null || player.isSpectator() || player.getAbilities().flying || player.isSwimming() || player.isPassenger()) {
         return false;
      } else if (flyOn()) {
         return false;
      } else if (this.dipOnSneak.get() && player.isShiftKeyDown()) {
         return false;
      } else {
         int height = this.dipFallHeight.get();
         if (height > 0 && player.fallDistance > height) {
            return false;
         } else if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return this.water.get() && (!this.dipWhenBurning.get() || !player.isOnFire());
         } else if (fluid != Fluids.LAVA && fluid != Fluids.FLOWING_LAVA) {
            return false;
         } else if (!this.lava.get()) {
            return false;
         } else if (!this.lavaNeedsResistance.get()) {
            return true;
         } else {
            MobEffectInstance effect = player.getEffect(MobEffects.FIRE_RESISTANCE);
            return effect != null && effect.getDuration() > 300;
         }
      }
   }

   private static boolean flyOn() {
      Fly fly = Modules.get().get(Fly.class);
      return fly != null && fly.isFlying();
   }

   @EventHandler
   private void onCanWalkOnFluid(CanWalkOnFluidEvent event) {
      FluidState state = event.fluidState;
      if (this.solid(state.getType())) {
         event.walkOnFluid = true;
      }
   }

   @EventHandler
   private void onCollisionShape(CollisionShapeEvent event) {
      if (this.flowing.get()) {
         LocalPlayer player = this.mc.player;
         if (player != null) {
            FluidState fluid = event.state.getFluidState();
            if (!fluid.isEmpty() && !fluid.isSource() && event.state.is(fluid.createLegacyBlock().getBlock())) {
               if (!(event.pos.getY() > player.getY() - 1.0) && !player.isInWater() && !player.isInLava()) {
                  if (this.solid(fluid.getType())) {
                     event.shape = Shapes.block();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.rise.get()) {
         boolean inWater = player.isInWater() && this.solid(Fluids.WATER);
         boolean inLava = player.isInLava() && this.solid(Fluids.LAVA);
         if (inWater || inLava) {
            Vec3 v = player.getDeltaMovement();
            if (v.y < 0.11) {
               player.setDeltaMovement(v.x, 0.11, v.z);
            }
         }
      }
   }
}
