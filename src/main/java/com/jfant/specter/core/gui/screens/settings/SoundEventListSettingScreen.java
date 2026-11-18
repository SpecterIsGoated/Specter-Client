package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public class SoundEventListSettingScreen extends CollectionListSettingScreen<SoundEvent> {
   public SoundEventListSettingScreen(GuiTheme theme, Setting<List<SoundEvent>> setting) {
      super(theme, "Select Sounds", setting, setting.get(), BuiltInRegistries.SOUND_EVENT);
   }

   protected WWidget getValueWidget(SoundEvent value) {
      return this.theme.label(value.location().getPath());
   }

   protected String[] getValueNames(SoundEvent value) {
      return new String[]{value.location().toString(), I18n.get("subtitles." + value.location().getPath())};
   }
}
