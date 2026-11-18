package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.systems.accounts.MicrosoftLogin;
import com.jfant.specter.core.systems.accounts.types.MicrosoftAccount;

public class AddMicrosoftAccountScreen extends AddAccountScreen {
   public AddMicrosoftAccountScreen(GuiTheme theme, AccountsScreen parent) {
      super(theme, "Add Microsoft Account", parent);
   }

   @Override
   public void initWidgets() {
      String url = MicrosoftLogin.getRefreshToken(refreshToken -> {
         if (refreshToken != null) {
            MicrosoftAccount account = new MicrosoftAccount(refreshToken);
            AccountsScreen.addAccount(null, this.parent, account);
         }

         this.onClose();
      });
      this.add(this.theme.label("Please select the account to log into in your browser."));
      this.add(this.theme.label("If the link does not automatically open in a few seconds, copy it into your browser."));
      WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
      WButton copy = l.add(this.theme.button("Copy link")).expandX().widget();
      copy.action = () -> SpecterCore.mc.keyboardHandler.setClipboard(url);
      WButton cancel = l.add(this.theme.button("Cancel")).expandX().widget();
      cancel.action = () -> {
         MicrosoftLogin.cancelLogin();
         this.onClose();
      };
   }

   @Override
   public void tick() {
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }
}
