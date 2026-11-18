package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.pvp.Pace;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;

public class WTap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<WTap.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("W Tap: let go of forward. S Tap: let go of forward and press back."))
               .defaultValue(WTap.Mode.WTap))
            .build()
      );
   private final Setting<Integer> hold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("hold")
            .description("Ticks the tap lasts, starting on the tick of the hit.")
            .defaultValue(1)
            .range(1, 3)
            .sliderRange(1, 3)
            .build()
      );
   private final Setting<Double> chance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("chance")
            .description("Percent of sprint hits that get a tap. Below 100 so not every single hit does.")
            .defaultValue(100.0)
            .range(0.0, 100.0)
            .sliderRange(0.0, 100.0)
            .build()
      );
   private final Setting<Boolean> debug = this.sgDebug
      .add(new BoolSetting.Builder().name("debug").description("Say in chat (only you see it) each tap and why a hit got none.").defaultValue(false).build());
   private int remaining;

   public WTap() {
      super(SpecterClientAddon.PVP, "w-tap", "After each sprint hit, lets go of W (or taps S) for a tick so your next hit is a sprint hit again.");
   }

   @Override
   public void onDeactivate() {
      if (this.remaining > 0) {
         this.restore();
      }

      this.remaining = 0;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.player != null && event.entity instanceof LivingEntity) {
         if (this.mc.player.isSprinting() && !(this.mc.player.getAttackStrengthScale(0.5F) <= 0.9F) && Input.isPressed(this.mc.options.keyUp)) {
            if (!Pace.chance(this.chance.get())) {
               if (this.debug.get()) {
                  this.info("No tap: chance.", new Object[0]);
               }
            } else {
               this.remaining = this.hold.get();
               this.release();
               if (this.debug.get()) {
                  this.info("Tap for %d tick%s.", new Object[]{this.remaining, this.remaining == 1 ? "" : "s"});
               }
            }
         } else {
            if (this.debug.get()) {
               this.info("No tap: not a full sprint hit with forward held.", new Object[0]);
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.remaining > 0) {
         this.remaining--;
         if (this.remaining != 0 && this.mc.screen == null) {
            this.release();
         } else {
            this.remaining = 0;
            this.restore();
         }
      }
   }

   private void release() {
      this.mc.options.keyUp.setDown(false);
      if (this.mode.get() == WTap.Mode.STap) {
         this.mc.options.keyDown.setDown(true);
      }
   }

   private void restore() {
      boolean free = this.mc.screen == null;
      this.mc.options.keyUp.setDown(free && Input.isPressed(this.mc.options.keyUp));
      this.mc.options.keyDown.setDown(free && Input.isPressed(this.mc.options.keyDown));
   }

   public static enum Mode {
      WTap("W Tap"),
      STap("S Tap");

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
