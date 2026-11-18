package com.jfant.specter.core.utils.render.color;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.PostInit;
import com.jfant.specter.core.utils.misc.UnorderedArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;

public class RainbowColors {
   private static final List<Setting<SettingColor>> colorSettings = new UnorderedArrayList<>();
   private static final List<Setting<List<SettingColor>>> colorListSettings = new UnorderedArrayList<>();
   private static final List<SettingColor> colors = new UnorderedArrayList<>();
   private static final List<Runnable> listeners = new UnorderedArrayList<>();
   public static final RainbowColor GLOBAL = new RainbowColor();

   private RainbowColors() {
   }

   @PostInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(RainbowColors.class);
   }

   public static void addSetting(Setting<SettingColor> setting) {
      colorSettings.add(setting);
   }

   public static void addSettingList(Setting<List<SettingColor>> setting) {
      colorListSettings.add(setting);
   }

   public static void removeSetting(Setting<SettingColor> setting) {
      colorSettings.remove(setting);
   }

   public static void removeSettingList(Setting<List<SettingColor>> setting) {
      colorListSettings.remove(setting);
   }

   public static void add(SettingColor color) {
      colors.add(color);
   }

   public static void register(Runnable runnable) {
      listeners.add(runnable);
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      GLOBAL.setSpeed(Config.get().rainbowSpeed.get() / 100.0);
      GLOBAL.getNext();

      for (Setting<SettingColor> setting : colorSettings) {
         if (setting.module == null || setting.module.isActive()) {
            setting.get().update();
         }
      }

      for (Setting<List<SettingColor>> settingx : colorListSettings) {
         if (settingx.module == null || settingx.module.isActive()) {
            for (SettingColor color : settingx.get()) {
               color.update();
            }
         }
      }

      for (SettingColor color : colors) {
         color.update();
      }

      if (SpecterCore.mc.screen instanceof WidgetScreen) {
         for (SettingGroup group : GuiThemes.get().settings) {
            for (Setting<?> settingxx : group) {
               if (settingxx instanceof ColorSetting) {
                  ((SettingColor)settingxx.get()).update();
               }
            }
         }
      }

      for (Runnable listener : listeners) {
         listener.run();
      }
   }
}
