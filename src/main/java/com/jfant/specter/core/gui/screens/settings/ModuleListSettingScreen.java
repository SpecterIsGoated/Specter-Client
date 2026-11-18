package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import java.util.List;

public class ModuleListSettingScreen extends CollectionListSettingScreen<Module> {
   public ModuleListSettingScreen(GuiTheme theme, Setting<List<Module>> setting) {
      super(theme, "Select Modules", setting, setting.get(), Modules.get().getAll());
   }

   protected WWidget getValueWidget(Module value) {
      return this.theme.label(value.title);
   }

   protected String[] getValueNames(Module value) {
      String[] names = new String[value.aliases.length + 1];
      System.arraycopy(value.aliases, 0, names, 1, value.aliases.length);
      names[0] = value.title;
      return names;
   }
}
