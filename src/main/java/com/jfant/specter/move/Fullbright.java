package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Fullbright extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Fullbright.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Gamma: raises the game's brightness curve, like an extremely high brightness slider; colours stay natural. Night Vision: the same lighting a night-vision potion gives, scaled to the brightness below."
                  ))
               .defaultValue(Fullbright.Mode.NightVision))
            .build()
      );
   private final Setting<Integer> brightness = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("brightness")
            .description("How bright, in percent. 100 lights even a pitch-black cave completely; lower keeps some difference between lit and dark places.")
            .defaultValue(100)
            .range(5, 100)
            .sliderRange(5, 100)
            .build()
      );
   private final Setting<Double> fadeTime = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description(
               "Seconds the light takes to come up or go back down. 0 switches at once. The Animations settings in Client can shorten or turn this off altogether."
            )
            .defaultValue(0.9)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .decimalPlaces(1)
            .build()
      );
   private final Brightness.Layer layer = Brightness.layer();
   private Spec cachedFade = Spec.WORLD_FADE;
   private double cachedSeconds = 0.9;

   public Fullbright() {
      super(
         SpecterClientAddon.VISUALS,
         "fullbright",
         "Lights up the whole world, caves and nights included. Only changes how your screen is lit (the server never sees it); the light fades in and out."
      );
   }

   @Override
   public void onActivate() {
      this.apply();
   }

   @Override
   public void onDeactivate() {
      this.layer.set(0.0, 0.0, this.fade());
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.layer.snapOff();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.apply();
   }

   private void apply() {
      double level = this.brightness.get().intValue() / 100.0;
      if (this.mode.get() == Fullbright.Mode.NightVision) {
         this.layer.set(level, 0.0, this.fade());
      } else {
         this.layer.set(0.0, level * 40.0, this.fade());
      }
   }

   private Spec fade() {
      double seconds = this.fadeTime.get();
      if (seconds != this.cachedSeconds) {
         this.cachedSeconds = seconds;
         this.cachedFade = Spec.WORLD_FADE.lasting(seconds * 1000.0);
      }

      return this.cachedFade;
   }

   @Override
   public String getInfoString() {
      return this.mode.get().toString();
   }

   public static enum Mode {
      Gamma("Gamma"),
      NightVision("Night Vision");

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
