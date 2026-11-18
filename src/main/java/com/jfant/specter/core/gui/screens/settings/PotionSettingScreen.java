package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.settings.PotionSetting;
import com.jfant.specter.core.utils.misc.MyPotion;
import net.minecraft.client.resources.language.I18n;

public class PotionSettingScreen extends WindowScreen {
   private final PotionSetting setting;

   public PotionSettingScreen(GuiTheme theme, PotionSetting setting) {
      super(theme, "Select Potion");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTable table = this.add(this.theme.table()).expandX().widget();

      for (MyPotion potion : MyPotion.values()) {
         table.add(this.theme.itemWithLabel(potion.potion, I18n.get(potion.potion.getItem().getDescriptionId())));
         WButton select = table.add(this.theme.button("Select")).widget();
         select.action = () -> {
            this.setting.set(potion);
            this.onClose();
         };
         table.row();
      }
   }
}
