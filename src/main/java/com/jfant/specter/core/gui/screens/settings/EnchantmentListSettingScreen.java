package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.DynamicRegistryListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.utils.misc.Names;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentListSettingScreen extends DynamicRegistryListSettingScreen<Enchantment> {
   public EnchantmentListSettingScreen(GuiTheme theme, Setting<Set<ResourceKey<Enchantment>>> setting) {
      super(theme, "Select Enchantments", setting, setting.get(), Registries.ENCHANTMENT);
   }

   protected WWidget getValueWidget(ResourceKey<Enchantment> value) {
      return this.theme.label(Names.get(value));
   }

   protected String[] getValueNames(ResourceKey<Enchantment> value) {
      return new String[]{Names.get(value), value.identifier().toString()};
   }
}
