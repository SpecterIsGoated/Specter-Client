package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.utils.misc.Names;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ItemListSettingScreen extends CollectionListSettingScreen<Item> {
   public ItemListSettingScreen(GuiTheme theme, ItemListSetting setting) {
      super(theme, "Select Items", setting, setting.get(), BuiltInRegistries.ITEM);
   }

   protected boolean includeValue(Item value) {
      Predicate<Item> filter = ((ItemListSetting)this.setting).filter;
      return filter != null && !filter.test(value) ? false : value != Items.AIR;
   }

   protected WWidget getValueWidget(Item value) {
      return this.theme.itemWithLabel(value.getDefaultInstance());
   }

   protected String[] getValueNames(Item value) {
      return new String[]{Names.get(value), BuiltInRegistries.ITEM.getKey(value).toString()};
   }
}
