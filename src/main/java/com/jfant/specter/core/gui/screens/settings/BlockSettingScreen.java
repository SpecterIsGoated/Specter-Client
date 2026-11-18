package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.WItemWithLabel;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.settings.BlockSetting;
import com.jfant.specter.core.utils.misc.Names;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.Strings;

public class BlockSettingScreen extends WindowScreen {
   private final BlockSetting setting;
   private WTable table;
   private WTextBox filter;
   private String filterText = "";

   public BlockSettingScreen(GuiTheme theme, BlockSetting setting) {
      super(theme, "Select Block");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   private void initTable() {
      for (Block block : BuiltInRegistries.BLOCK) {
         if ((this.setting.filter == null || this.setting.filter.test(block)) && !this.skipValue(block)) {
            WItemWithLabel item = this.theme.itemWithLabel(block.asItem().getDefaultInstance(), Names.get(block));
            if (this.filterText.isEmpty() || Strings.CI.contains(item.getLabelText(), this.filterText)) {
               this.table.add(item);
               WButton select = this.table.add(this.theme.button("Select")).expandCellX().right().widget();
               select.action = () -> {
                  this.setting.set(block);
                  this.onClose();
               };
               this.table.row();
            }
         }
      }
   }

   protected boolean skipValue(Block value) {
      return value == Blocks.AIR || BuiltInRegistries.BLOCK.getKey(value).getPath().endsWith("_wall_banner");
   }
}
