package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.utils.misc.Names;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ParticleTypeListSettingScreen extends CollectionListSettingScreen<ParticleType<?>> {
   public ParticleTypeListSettingScreen(GuiTheme theme, Setting<List<ParticleType<?>>> setting) {
      super(theme, "Select Particles", setting, setting.get(), BuiltInRegistries.PARTICLE_TYPE);
   }

   protected boolean includeValue(ParticleType<?> value) {
      return value instanceof ParticleOptions;
   }

   protected WWidget getValueWidget(ParticleType<?> value) {
      return this.theme.label(Names.get(value));
   }

   protected String[] getValueNames(ParticleType<?> value) {
      return new String[]{Names.get(value), BuiltInRegistries.PARTICLE_TYPE.getKey(value).toString()};
   }
}
