package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;

public class SwingSpeed extends Module {
   private static final double VANILLA_LEVEL = 5.0;
   private static SwingSpeed instance;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> level = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("level")
            .description("How fast your arm swings: 5 is normal, 10 is twice as fast, 1 is a fifth of the speed.")
            .defaultValue(1.0)
            .range(1.0, 10.0)
            .sliderRange(1.0, 10.0)
            .build()
      );

   public SwingSpeed() {
      super(SpecterClientAddon.TOOLS, "swing-speed", "Makes your arm swing and mining animation faster or slower, on your screen only.");
      instance = this;
   }

   public static int swingTicks(int vanillaTicks) {
      SwingSpeed self = instance;
      if (self != null && self.isActive()) {
         double speed = self.level.get() / 5.0;
         return (int)Math.max(1L, Math.round(vanillaTicks / speed));
      } else {
         return vanillaTicks;
      }
   }
}
