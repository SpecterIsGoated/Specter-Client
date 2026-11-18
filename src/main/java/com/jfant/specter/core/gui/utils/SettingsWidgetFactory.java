package com.jfant.specter.core.gui.utils;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.Settings;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class SettingsWidgetFactory {
   private static final Map<Class<?>, Function<GuiTheme, SettingsWidgetFactory.Factory>> customFactories = new HashMap<>();
   protected final GuiTheme theme;
   protected final Map<Class<?>, SettingsWidgetFactory.Factory> factories = new HashMap<>();

   public SettingsWidgetFactory(GuiTheme theme) {
      this.theme = theme;
   }

   public static void registerCustomFactory(Class<?> settingClass, Function<GuiTheme, SettingsWidgetFactory.Factory> factoryFunction) {
      customFactories.put(settingClass, factoryFunction);
   }

   public static void unregisterCustomFactory(Class<?> settingClass) {
      customFactories.remove(settingClass);
   }

   public abstract WWidget create(GuiTheme var1, Settings var2, String var3);

   protected SettingsWidgetFactory.Factory getFactory(Class<?> settingClass) {
      return customFactories.containsKey(settingClass) ? customFactories.get(settingClass).apply(this.theme) : this.factories.get(settingClass);
   }

   @FunctionalInterface
   public interface Factory {
      void create(WTable var1, Setting<?> var2);
   }
}
