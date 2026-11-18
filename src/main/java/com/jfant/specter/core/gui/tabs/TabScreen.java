package com.jfant.specter.core.gui.tabs;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.utils.Cell;
import com.jfant.specter.core.gui.widgets.WWidget;

public abstract class TabScreen extends WidgetScreen {
   public final Tab tab;

   public TabScreen(GuiTheme theme, Tab tab) {
      super(theme, tab.name);
      this.tab = tab;
   }

   public <T extends WWidget> Cell<T> addDirect(T widget) {
      return super.add(widget);
   }
}
