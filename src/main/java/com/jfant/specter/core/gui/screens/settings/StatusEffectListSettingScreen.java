package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.screens.settings.base.CollectionListSettingScreen;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.utils.misc.Names;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class StatusEffectListSettingScreen extends CollectionListSettingScreen<MobEffect> {
   public StatusEffectListSettingScreen(GuiTheme theme, Setting<List<MobEffect>> setting) {
      super(theme, "Select Effects", setting, setting.get(), BuiltInRegistries.MOB_EFFECT);
   }

   protected WWidget getValueWidget(MobEffect value) {
      return this.theme.itemWithLabel(this.getPotionStack(value), Names.get(value));
   }

   protected String[] getValueNames(MobEffect value) {
      return new String[]{Names.get(value), BuiltInRegistries.MOB_EFFECT.getKey(value).toString()};
   }

   private ItemStack getPotionStack(MobEffect effect) {
      ItemStack potion = Items.POTION.getDefaultInstance();
      potion.set(
         DataComponents.POTION_CONTENTS,
         new PotionContents(
            potion.get(DataComponents.POTION_CONTENTS).potion(),
            Optional.of(effect.getColor()),
            potion.get(DataComponents.POTION_CONTENTS).customEffects(),
            Optional.empty()
         )
      );
      return potion;
   }
}
