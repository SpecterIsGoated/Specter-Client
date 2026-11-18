package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.AccountType;
import com.jfant.specter.core.systems.accounts.TokenAccount;
import com.jfant.specter.core.utils.render.color.Color;

public class AccountInfoScreen extends WindowScreen {
   private final Account<?> account;

   public AccountInfoScreen(GuiTheme theme, Account<?> account) {
      super(theme, account.getUsername() + " details");
      this.account = account;
   }

   @Override
   public void initWidgets() {
      TokenAccount e = (TokenAccount)this.account;
      WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
      String tokenLabel = this.account.getType() + " token:";
      if (this.account.getType() == AccountType.Session) {
         tokenLabel = "";
      }

      WButton copy = this.theme.button("Copy");
      copy.action = () -> SpecterCore.mc.keyboardHandler.setClipboard(e.getToken());
      l.add(this.theme.label(tokenLabel));
      l.add(this.theme.label(this.account.getType() == AccountType.Session ? "Click to copy Token" : e.getToken()).color(Color.GRAY)).pad(5.0);
      l.add(copy);
   }
}
