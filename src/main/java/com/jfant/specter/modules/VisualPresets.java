package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.festive.Season;
import com.jfant.specter.notify.Notifications;
import java.util.ArrayList;
import java.util.List;

public class VisualPresets extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<VisualPresets.Preset> preset = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("preset"))
                     .description(
                        "The look this module switches on. Christmas: snow, a cold grade, the GUI's Christmas dress. Tropical Night: glowing water and haze. Coastal Morning: sea fog and faded film. Cinematic: a film grade."
                     ))
                  .defaultValue(VisualPresets.Preset.Christmas))
               .onChanged(value -> {
                  if (this.isActive() && this.applied != null && this.applied != value) {
                     this.undo();
                     this.apply(value);
                  }
               }))
            .build()
      );
   private final List<Runnable> restore = new ArrayList<>();
   private VisualPresets.Preset applied;

   public VisualPresets() {
      super(
         SpecterClientAddon.VISUALS,
         "visual-presets",
         "A whole look in one switch: Christmas, Tropical Night, Coastal Morning or Cinematic. Off puts everything back as it was."
      );
   }

   @Override
   public void onActivate() {
      this.apply(this.preset.get());
   }

   @Override
   public void onDeactivate() {
      this.undo();
   }

   private void apply(VisualPresets.Preset look) {
      this.restore.clear();
      this.applied = look;
      switch (look) {
         case Christmas: {
            Config config = Config.get();
            this.change(config.season, Season.Christmas);
            this.change(config.guiSnow, true);
            Snowfall snow = Modules.get().get(Snowfall.class);
            this.set(snow, "where", Snowfall.Where.Both);
            this.turnOn(snow);
            ColorGrading grade = Modules.get().get(ColorGrading.class);
            this.set(grade, "look", ColorGrading.Look.Winter);
            this.turnOn(grade);
            break;
         }
         case Tropical: {
            Bioluminescence glow = Modules.get().get(Bioluminescence.class);
            this.set(glow, "palette", Bioluminescence.Palette.Tide);
            this.turnOn(glow);
            MarineLayer fog = Modules.get().get(MarineLayer.class);
            this.set(fog, "preset", MarineLayer.Preset.Haze);
            this.turnOn(fog);
            ColorGrading grade = Modules.get().get(ColorGrading.class);
            this.set(grade, "look", ColorGrading.Look.Emerald);
            this.turnOn(grade);
            break;
         }
         case Coastal: {
            MarineLayer fog = Modules.get().get(MarineLayer.class);
            this.set(fog, "preset", MarineLayer.Preset.Coastal);
            this.turnOn(fog);
            ColorGrading grade = Modules.get().get(ColorGrading.class);
            this.set(grade, "look", ColorGrading.Look.Film);
            this.turnOn(grade);
            break;
         }
         case Cinematic: {
            ColorGrading grade = Modules.get().get(ColorGrading.class);
            this.set(grade, "look", ColorGrading.Look.Cinematic);
            this.turnOn(grade);
            MarineLayer fog = Modules.get().get(MarineLayer.class);
            this.set(fog, "preset", MarineLayer.Preset.Haze);
            this.turnOn(fog);
         }
      }

      Notifications.keyed(this, Notifications.Kind.SUCCESS, "Visual Presets", look.title + ": " + look.about);
   }

   private void undo() {
      for (int i = this.restore.size() - 1; i >= 0; i--) {
         this.restore.get(i).run();
      }

      this.restore.clear();
      this.applied = null;
   }

   private <T> void change(Setting<T> setting, T value) {
      T before = setting.get();
      if (before == null || !before.equals(value)) {
         this.restore.add(() -> setting.set(before));
         setting.set(value);
      }
   }

   private <T> void set(Module module, String name, T value) {
      if (module != null) {
         Setting<?> setting = module.settings.get(name);
         if (setting != null) {
            this.change(setting, value);
         }
      }
   }

   private void turnOn(Module module) {
      if (module != null && !module.isActive()) {
         this.restore.add(() -> {
            if (module.isActive()) {
               module.toggle();
            }
         });
         module.toggle();
      }
   }

   public static enum Preset {
      Christmas("Christmas", "Snow in the world and across the screen, a cold clear grade, and the click GUI in its Christmas dress."),
      Tropical("Tropical Night", "Glowing water, a light sea haze and a lush green grade."),
      Coastal("Coastal Morning", "A marine layer over the water and the washed colours of old film."),
      Cinematic("Cinematic", "A film grade - teal shadows, warm highlights, a vignette - and a light haze for depth.");

      private final String title;
      private final String about;

      private Preset(String title, String about) {
         this.title = title;
         this.about = about;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
