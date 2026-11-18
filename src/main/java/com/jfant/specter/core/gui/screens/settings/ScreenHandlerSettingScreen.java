package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public class ScreenHandlerSettingScreen extends CollectionListSettingScreen<MenuType<?>> {
   public ScreenHandlerSettingScreen(GuiTheme theme, Setting<List<MenuType<?>>> setting) {
      super(theme, "Select Screen Handlers", setting, setting.get(), BuiltInRegistries.MENU);
   }

   protected WWidget getValueWidget(MenuType<?> value) {
      return this.theme.label(getName(value));
   }

   protected String[] getValueNames(MenuType<?> type) {
      return new String[]{getName(type)};
   }

   private static String getName(MenuType<?> type) {
      return BuiltInRegistries.MENU.getKey(type).toString();
   }
}
