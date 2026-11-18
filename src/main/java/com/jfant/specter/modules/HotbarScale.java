package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import net.minecraft.client.Minecraft;

public class HotbarScale extends Module {
   private static final int HOTBAR_HEIGHT = 22;
   private static HotbarScale instance;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> scale = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("scale")
            .description("GUI scale for the hotbar, like the vanilla GUI Scale option. Capped at the largest scale your window fits.")
            .defaultValue(2)
            .range(1, 8)
            .sliderRange(1, 6)
            .build()
      );
   private final Setting<Boolean> moveBars = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("move-bars")
            .description("Keep hearts, hunger, armor, air and the XP bar sitting right on top of the hotbar. Off leaves them where they normally are.")
            .defaultValue(true)
            .build()
      );

   public HotbarScale() {
      super(SpecterClientAddon.HUD, "hotbar-scale", "Gives the hotbar its own GUI scale, separate from the rest of the HUD.");
      instance = this;
   }

   public static float ratio() {
      if (instance != null && instance.isActive()) {
         Minecraft mc = Minecraft.getInstance();
         int max = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
         return (float)Math.min(instance.scale.get(), max) / mc.getWindow().getGuiScale();
      } else {
         return 1.0F;
      }
   }

   public static float barsOffset(float ratio) {
      return !instance.moveBars.get() ? 0.0F : -22.0F * (ratio - 1.0F);
   }
}
