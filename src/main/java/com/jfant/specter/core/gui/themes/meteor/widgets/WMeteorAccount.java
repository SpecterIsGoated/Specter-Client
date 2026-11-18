package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WAccount;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorAccount extends WAccount implements MeteorWidget {
   public WMeteorAccount(WidgetScreen screen, Account<?> account) {
      super(screen, account);
   }

   @Override
   protected Color loggedInColor() {
      return this.theme().loggedInColor.get();
   }

   @Override
   protected Color accountTypeColor() {
      return this.theme().textSecondaryColor.get();
   }
}
