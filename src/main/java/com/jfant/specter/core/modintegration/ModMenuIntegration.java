package com.jfant.specter.core.modintegration;

import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.screens.ModulesScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return screen -> new ModulesScreen(GuiThemes.get());
   }
}
