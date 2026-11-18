package com.jfant.specter.core.gui.screens.settings;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.utils.Cell;
import com.jfant.specter.core.gui.widgets.WLabel;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.containers.WView;
import com.jfant.specter.core.gui.widgets.input.WDropdown;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.renderer.text.FontFamily;
import com.jfant.specter.core.renderer.text.FontInfo;
import com.jfant.specter.core.settings.FontFaceSetting;
import java.util.List;
import org.apache.commons.lang3.Strings;

public class FontFaceSettingScreen extends WindowScreen {
   private final FontFaceSetting setting;
   private WTable table;
   private WTextBox filter;
   private String filterText = "";

   public FontFaceSettingScreen(GuiTheme theme, FontFaceSetting setting) {
      super(theme, "Select Font");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.window.view.hasScrollBar = false;
      this.enterAction = () -> {
         List<Cell<?>> row = this.table.getRow(0);
         if (row != null) {
            if (row.get(2).widget() instanceof WButton button) {
               button.action.run();
            }
         }
      };
      WView view = this.add(this.theme.view()).expandX().widget();
      view.maxHeight = this.window.view.maxHeight - 128.0;
      view.scrollOnlyWhenMouseOver = false;
      this.table = view.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   private void initTable() {
      for (FontFamily fontFamily : Fonts.FONT_FAMILIES) {
         String name = fontFamily.getName();
         WLabel item = this.theme.label(name);
         if (this.filterText.isEmpty() || Strings.CI.contains(name, this.filterText)) {
            this.table.add(item);
            WDropdown<FontInfo.Type> dropdown = this.table.add(this.theme.dropdown(FontInfo.Type.Regular)).right().widget();
            WButton select = this.table.add(this.theme.button("Select")).expandCellX().right().widget();
            select.action = () -> {
               this.setting.set(fontFamily.get(dropdown.get()));
               this.onClose();
            };
            this.table.row();
         }
      }
   }
}
